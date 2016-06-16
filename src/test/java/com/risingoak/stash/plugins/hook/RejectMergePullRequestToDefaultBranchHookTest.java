package com.risingoak.stash.plugins.hook;

import com.atlassian.bitbucket.build.BuildStatusService;
import com.atlassian.bitbucket.commit.Commit;
import com.atlassian.bitbucket.commit.CommitService;
import com.atlassian.bitbucket.commit.CommitsRequest;
import com.atlassian.bitbucket.hook.repository.RepositoryMergeRequestCheckContext;
import com.atlassian.bitbucket.pull.PullRequest;
import com.atlassian.bitbucket.pull.PullRequestRef;
import com.atlassian.bitbucket.repository.Branch;
import com.atlassian.bitbucket.repository.RefService;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.scm.pull.MergeRequest;
import com.atlassian.bitbucket.util.Page;
import com.atlassian.bitbucket.util.PageRequest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.ArrayList;

import static org.mockito.Mockito.*;


@RunWith(MockitoJUnitRunner.class)
public class RejectMergePullRequestToDefaultBranchHookTest {

    public static final String DEFAULT_BRANCH_REF = "refs/heads/master";

    @Mock
    private RefService refService;
    @Mock
    private BuildStatusService buildStatusService;
    @Mock
    private CommitService commitService;

    @Mock
    private RepositoryMergeRequestCheckContext repositoryHookContext;
    @Mock
    private Branch branch;
    @Mock
    private Repository repository;
    @Mock
    private MergeRequest mergeRequest;
    @Mock
    private PullRequest pullRequest;
    @Mock
    private PullRequestRef toRef;

    @Before
    public void setUp() {
        when(repositoryHookContext.getMergeRequest()).thenReturn(mergeRequest);
        when(mergeRequest.getPullRequest()).thenReturn(pullRequest);
        when(pullRequest.getToRef()).thenReturn(toRef);
        when(toRef.getRepository()).thenReturn(repository);
        when(refService.getDefaultBranch(isA(Repository.class))).thenReturn(branch);
        when(branch.getId()).thenReturn(DEFAULT_BRANCH_REF);
        when(branch.getLatestCommit()).thenReturn("latestcommit");
        Page<Commit> commitsPage = mock(Page.class);
        when(commitsPage.getValues()).thenReturn(new ArrayList<>());
        when(commitService.getCommits(any(CommitsRequest.class), any(PageRequest.class)))
                .thenReturn(commitsPage);
    }

    @Test
    public void allowMerge_mostRecentBuildIsSuccessful() {
        RejectMergePullRequestToDefaultBranchHook buildHook = mockBuildHook(new AbstractRejectHook.BranchState(AbstractRejectHook.BuildState0.SUCCESSFUL, "hash"));

        buildHook.check(repositoryHookContext);

        verify(mergeRequest, never()).veto(anyString(), anyString());
    }

    @Test
    public void allowMerge_mostRecentBuildIsUndefined() {
        RejectMergePullRequestToDefaultBranchHook buildHook = mockBuildHook(new AbstractRejectHook.BranchState(AbstractRejectHook.BuildState0.UNDEFINED, "hash"));

        buildHook.check(repositoryHookContext);

        verify(mergeRequest, never()).veto(anyString(), anyString());
    }

    @Test
    public void rejectMerge_mostRecentBuildIsInProgress() {
        RejectMergePullRequestToDefaultBranchHook buildHook = mockBuildHook(new AbstractRejectHook.BranchState(AbstractRejectHook.BuildState0.INPROGRESS, "hash"));

        buildHook.check(repositoryHookContext);

        verify(mergeRequest).veto(anyString(), anyString());
    }

    @Test
    public void rejectMerge_mostRecentBuildIsFailed() {
        RejectMergePullRequestToDefaultBranchHook buildHook = mockBuildHook(new AbstractRejectHook.BranchState(AbstractRejectHook.BuildState0.FAILED, "hash"));

        buildHook.check(repositoryHookContext);

        verify(mergeRequest).veto(anyString(), anyString());
    }

    private RejectMergePullRequestToDefaultBranchHook mockBuildHook(final AbstractRejectHook.BranchState branchState) {
        return new RejectMergePullRequestToDefaultBranchHook(refService, buildStatusService, commitService) {
            @Override
            protected BranchState getAggregatedStatus(Page<Commit> commits) {
                return branchState;
            }
        };
    }
}