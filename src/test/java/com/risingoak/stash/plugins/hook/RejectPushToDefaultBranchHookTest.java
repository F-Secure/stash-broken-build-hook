package com.risingoak.stash.plugins.hook;

import com.atlassian.bitbucket.build.BuildState;
import com.atlassian.bitbucket.build.BuildStatus;
import com.atlassian.bitbucket.build.BuildStatusService;
import com.atlassian.bitbucket.commit.Commit;
import com.atlassian.bitbucket.commit.CommitRequest;
import com.atlassian.bitbucket.commit.CommitService;
import com.atlassian.bitbucket.commit.CommitsRequest;
import com.atlassian.bitbucket.hook.HookResponse;
import com.atlassian.bitbucket.hook.repository.RepositoryHookContext;
import com.atlassian.bitbucket.repository.*;
import com.atlassian.bitbucket.util.Page;
import com.atlassian.bitbucket.util.PageRequest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class RejectPushToDefaultBranchHookTest {

    public static final String DEFAULT_BRANCH_REF = "refs/heads/master";

    @Mock
    private RefService refService;
    @Mock
    private BuildStatusService buildStatusService;
    @Mock
    private CommitService commitService;

    @InjectMocks
    private RejectPushToDefaultBranchHook rejectPushToDefaultBranchHook;

    @Mock
    private RepositoryHookContext repositoryHookContext;
    @Mock
    private HookResponse hookResponse;
    @Mock
    private Branch branch;
    @Mock
    private Repository repository;

    @Before
    public void setUp() {
        when(repositoryHookContext.getRepository()).thenReturn(repository);
        when(refService.getDefaultBranch(isA(Repository.class))).thenReturn(branch);
        when(branch.getId()).thenReturn(DEFAULT_BRANCH_REF);
        when(hookResponse.err()).thenReturn(new PrintWriter(new StringWriter()));
    }

    @Test
    public void shouldIgnorePushesThatDoNotAffectTheDefaultBranch() {
        SimpleRefChange refChange = getRefChangeFor("refs/heads/foobarbaz");
        boolean response = rejectPushToDefaultBranchHook.onReceive(repositoryHookContext, Arrays.asList(refChange), hookResponse);
        assertTrue("hook incorrectly rejected push to non-default branch", response);
        verifyZeroInteractions(commitService, buildStatusService);
    }

    @Test
    public void shouldAllowPushIfCommitBeingPushedHasSuccessfulBuild() {
        SimpleRefChange refChange = getRefChangeFor(DEFAULT_BRANCH_REF);
        Page page = mockBuildStatusList(BuildState.SUCCESSFUL);
        when(buildStatusService.findAll(refChange.getToHash())).thenReturn(page);
        boolean response = rejectPushToDefaultBranchHook.onReceive(repositoryHookContext, Arrays.asList(refChange), hookResponse);
        assertTrue("hook incorrectly rejected push that passes build", response);
        verify(buildStatusService).findAll(refChange.getToHash());
        verifyZeroInteractions(commitService);
    }

    @Test
    public void shouldAllowPushIfMostRecentBuildIsSuccessful() {
        SimpleRefChange refChange = mockSimpleRefChangeWithPriorBuildStates(BuildState.SUCCESSFUL);

        boolean response = rejectPushToDefaultBranchHook.onReceive(repositoryHookContext, Arrays.asList(refChange), hookResponse);
        assertTrue("hook incorrectly rejected push", response);
        verify(buildStatusService).findAll(refChange.getToHash());
        verify(buildStatusService).findAll(refChange.getFromHash());
    }

    @Test
    public void shouldRejectPushIfMostRecentBuildFailed() {
        final SimpleRefChange refChange = mockSimpleRefChangeWithPriorBuildStates(BuildState.FAILED);
        Commit topCommitInPush = mock(Commit.class);
        when(commitService.getCommit(argThat(new ArgumentMatcher<CommitRequest>() {
            @Override
            public boolean matches(Object o) {
                return ((CommitRequest) o).getCommitId().equals(refChange.getToHash());
            }
        }))).thenReturn(topCommitInPush);
        when(topCommitInPush.getMessage()).thenReturn("");

        boolean response = rejectPushToDefaultBranchHook.onReceive(repositoryHookContext, Arrays.asList(refChange), hookResponse);
        assertFalse("hook incorrectly allowed push", response);
        verify(buildStatusService).findAll(refChange.getToHash());
        verify(buildStatusService).findAll(refChange.getFromHash());
    }

    @Test
    public void shouldAllowPushIfMostRecentNonPendingBuildIsSuccessful() {
        SimpleRefChange refChange = mockSimpleRefChangeWithPriorBuildStates(BuildState.INPROGRESS, BuildState.INPROGRESS, BuildState.SUCCESSFUL);

        boolean response = rejectPushToDefaultBranchHook.onReceive(repositoryHookContext, Arrays.asList(refChange), hookResponse);
        assertTrue("hook incorrectly rejected push", response);
    }

    @Test
    public void shouldRejectPushIfMostRecentNonPendingBuildFailed() {
        final SimpleRefChange refChange = mockSimpleRefChangeWithPriorBuildStates(BuildState.INPROGRESS, BuildState.INPROGRESS, BuildState.FAILED);
        Commit topCommitInPush = mock(Commit.class);
        when(commitService.getCommit(argThat(new ArgumentMatcher<CommitRequest>() {
            @Override
            public boolean matches(Object o) {
                return ((CommitRequest) o).getCommitId().equals(refChange.getToHash());
            }
        }))).thenReturn(topCommitInPush);
        when(topCommitInPush.getMessage()).thenReturn("");

        boolean response = rejectPushToDefaultBranchHook.onReceive(repositoryHookContext, Arrays.asList(refChange), hookResponse);
        assertFalse("hook incorrectly allowed push", response);
    }

    @Test
    public void shouldRejectPushIfAllRecentBuildsArePending() {
        SimpleRefChange refChange = mockSimpleRefChangeWithPriorBuildStates(BuildState.INPROGRESS, BuildState.INPROGRESS, BuildState.INPROGRESS);

        boolean response = rejectPushToDefaultBranchHook.onReceive(repositoryHookContext, Arrays.asList(refChange), hookResponse);
        assertFalse("hook incorrectly allowed push", response);
    }

    @Test
    public void shouldAllowPushIfNoBuildInformationIsPresent() {
        SimpleRefChange refChange = mockSimpleRefChangeWithPriorBuildStates(null, null);

        boolean response = rejectPushToDefaultBranchHook.onReceive(repositoryHookContext, Arrays.asList(refChange), hookResponse);
        assertTrue("hook incorrectly rejected push", response);
    }

    private SimpleRefChange mockSimpleRefChangeWithPriorBuildStates(BuildState... states) {
        final SimpleRefChange refChange = getRefChangeFor(DEFAULT_BRANCH_REF);

        Page<BuildStatus> emptyPage = emptyBuildStatusList();
        when(buildStatusService.findAll(refChange.getToHash())).thenReturn(emptyPage);

        setBuildStateForHash(refChange.getFromHash(), states[0]);

        List<Commit> commits = new ArrayList<>();
        Commit commit = mockCommit(refChange.getFromHash());
        commits.add(commit);

        if (states.length > 1) {
            for (int idx = 1; idx < states.length; idx++) {
                BuildState state = states[idx];
                String hash = "hash-" + idx;
                commits.add(mockCommit(hash));
                setBuildStateForHash(hash, state);
            }
        }


        Page<Commit> commitsPage = mock(Page.class);
        when(commitsPage.getValues()).thenReturn(commits);
        when(commitService.getCommits(
                        argThat(new ArgumentMatcher<CommitsRequest>() {
                            @Override
                            public boolean matches(Object o) {
                                return ((CommitsRequest) o).getCommitId().equals(refChange.getFromHash());
                            }
                        }),
                        any(PageRequest.class))
        ).thenReturn(commitsPage);

        return refChange;
    }

    private void setBuildStateForHash(String hash, BuildState state) {
        Page<BuildStatus> page = mockBuildStatusList(state);
        when(buildStatusService.findAll(hash)).thenReturn(page);
    }

    private Commit mockCommit(String fromHash) {
        Commit commit = mock(Commit.class);
        when(commit.getId()).thenReturn(fromHash);
        return commit;
    }

    private Page<BuildStatus> emptyBuildStatusList() {
        Page<BuildStatus> page = mock(Page.class);
        List<BuildStatus> buildStatuses = new ArrayList<>();
        when(page.getValues()).thenReturn(buildStatuses);
        return page;
    }

    private Page<BuildStatus> mockBuildStatusList(BuildState... states) {
        Page<BuildStatus> page = mock(Page.class);
        List<BuildStatus> buildStatuses = new ArrayList<>();
        int idx = 0;
        for (BuildState state : states) {
            if (state != null) {
                buildStatuses.add(mockBuildStatus(state, "key-" + String.valueOf(idx++)));
            }
        }
        when(page.getValues()).thenReturn(buildStatuses);
        return page;
    }

    private BuildStatus mockBuildStatus(BuildState state, String key) {
        BuildStatus status = mock(BuildStatus.class);
        when(status.getState()).thenReturn(state);
        when(status.getKey()).thenReturn(key);
        when(status.getUrl()).thenReturn("http://example.com");
        when(status.getDateAdded()).thenReturn(new Date());
        return status;
    }

    private SimpleRefChange getRefChangeFor(String ref) {
        return new SimpleRefChange.Builder()
                .ref(new SimpleMinimalRef.Builder().id(ref).displayId(ref).type(StandardRefType.BRANCH).build())
                .fromHash("fromhash")
                .toHash("tohash")
                .type(RefChangeType.UPDATE)
                .build();
    }
}
