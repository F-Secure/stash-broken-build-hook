package com.risingoak.stash.plugins.hook;

import com.atlassian.bitbucket.build.BuildStatusService;
import com.atlassian.bitbucket.commit.Commit;
import com.atlassian.bitbucket.commit.CommitRequest;
import com.atlassian.bitbucket.commit.CommitService;
import com.atlassian.bitbucket.commit.CommitsRequest;
import com.atlassian.bitbucket.hook.HookResponse;
import com.atlassian.bitbucket.hook.repository.PreReceiveRepositoryHook;
import com.atlassian.bitbucket.hook.repository.RepositoryHookContext;
import com.atlassian.bitbucket.repository.Branch;
import com.atlassian.bitbucket.repository.RefChange;
import com.atlassian.bitbucket.repository.RefService;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.util.Page;
import com.atlassian.bitbucket.util.PageRequestImpl;

import javax.annotation.Nonnull;
import java.util.Collection;

public class RejectPushToDefaultBranchHook extends AbstractRejectHook implements PreReceiveRepositoryHook {

    public RejectPushToDefaultBranchHook(RefService refService, BuildStatusService buildStatusService, CommitService commitService) {
        super(refService, commitService, buildStatusService);
    }

    @Override
    public boolean onReceive(@Nonnull RepositoryHookContext repositoryHookContext, @Nonnull Collection<RefChange> refChanges, @Nonnull HookResponse hookResponse) {
        RefChange push = getPushToDefaultBranch(repositoryHookContext, refChanges);
        if (push == null) {
            return true;
        }

        String toHash = push.getToHash();

        // if for some reason we happen to have seen the status of the commit
        BuildState0 justPushedStatus = getAggregatedStatus(toHash);
        if (justPushedStatus == BuildState0.SUCCESSFUL) {
            return true;
        } else if (justPushedStatus == BuildState0.FAILED) {
            printPushingCommitWithFailedStatusMsg(hookResponse, toHash);
            return false;
        }

        Repository repository = repositoryHookContext.getRepository();
        BranchState defaultBranchState = getAggregatedStatus(getCommits(repository, push.getFromHash()));
        switch (defaultBranchState.state) {
            case INPROGRESS:
                printTooManyPendingBuilds(hookResponse, push);
                return false;
            case FAILED:
                if (isFix(repository, toHash, defaultBranchState.commit)) {
                    hookResponse.out().format("Build is broken at commit %s, but your push claims to fix it.\n", defaultBranchState.commit);
                    return true;
                } else {
                    printBranchHasFailedBuildMsg(hookResponse, push, defaultBranchState.commit);
                    return false;
                }
            case UNDEFINED:
                return true;
            case SUCCESSFUL:
                return true;
            default:
                return true;
        }
    }

    private Page<Commit> getCommits(Repository repository, String fromHash) {
        return commitService.getCommits(
                new CommitsRequest.Builder(repository, fromHash).build(),
                new PageRequestImpl(0, COMMITS_TO_INSPECT));
    }

    private boolean isFix(Repository repository, String head, String commit) {
        Commit mostRecentPushedCommit = commitService.getCommit(new CommitRequest.Builder(repository, head).build());
        String commitMessage = mostRecentPushedCommit.getMessage();
        return commitMessage != null && commitMessage.contains("fixes " + commit);
    }

    private void printPushingCommitWithFailedStatusMsg(HookResponse hookResponse, String toHash) {
        hookResponse.err().println();
        hookResponse.err().format("REJECTED: You are pushing a commit <%s> that has at least 1 failed build.\n", toHash);
    }

    private void printTooManyPendingBuilds(HookResponse hookResponse, RefChange push) {
        hookResponse.err().println();
        hookResponse.err().format("REJECTED: Too many pending builds on branch %s, wait a couple of minutes and try again.", push.getRefId());
    }

    private void printBranchHasFailedBuildMsg(HookResponse hookResponse, RefChange push, String fromHash) {
        hookResponse.err().println();
        hookResponse.err().format("REJECTED: Branch %s has at least 1 failed build for commit %s\n", push.getRefId(), fromHash);
        hookResponse.err().println();
        hookResponse.err().println("If you are fixing the build, amend your commit to contain the following message: ");
        hookResponse.err().println();
        hookResponse.err().format("'fixes %s'\n", fromHash);
    }

    private RefChange getPushToDefaultBranch(RepositoryHookContext repositoryHookContext, Collection<RefChange> refChanges) {
        Branch defaultBranch = refService.getDefaultBranch(repositoryHookContext.getRepository());
        for (RefChange refChange : refChanges) {
            if (refChange.getRefId().equals(defaultBranch.getId())) {
                return refChange;
            }
        }
        return null;
    }
}
