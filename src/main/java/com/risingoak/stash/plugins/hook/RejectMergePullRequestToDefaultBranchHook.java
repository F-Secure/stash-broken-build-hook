package com.risingoak.stash.plugins.hook;

import com.atlassian.bitbucket.build.BuildStatusService;
import com.atlassian.bitbucket.commit.Commit;
import com.atlassian.bitbucket.commit.CommitService;
import com.atlassian.bitbucket.commit.CommitsRequest;
import com.atlassian.bitbucket.hook.repository.RepositoryMergeRequestCheck;
import com.atlassian.bitbucket.hook.repository.RepositoryMergeRequestCheckContext;
import com.atlassian.bitbucket.repository.Branch;
import com.atlassian.bitbucket.repository.RefService;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.scm.pull.MergeRequest;
import com.atlassian.bitbucket.util.Page;
import com.atlassian.bitbucket.util.PageRequestImpl;

import javax.annotation.Nonnull;

import static java.lang.String.format;

public class RejectMergePullRequestToDefaultBranchHook extends AbstractRejectHook implements RepositoryMergeRequestCheck {

    public RejectMergePullRequestToDefaultBranchHook(RefService refService, BuildStatusService buildStatusService, CommitService commitService) {
        super(refService, commitService, buildStatusService);
    }

    @Override
    public void check(@Nonnull RepositoryMergeRequestCheckContext repositoryMergeRequestCheckContext) {
        MergeRequest mergeRequest = repositoryMergeRequestCheckContext.getMergeRequest();
        Repository repository = mergeRequest.getPullRequest().getToRef().getRepository();
        Branch defaultBranch = refService.getDefaultBranch(repository);
        String branchName = defaultBranch.getDisplayId();
        String latestCommitId = defaultBranch.getLatestCommit();

        Page<Commit> commits = commitService.getCommits(
                new CommitsRequest.Builder(repository, latestCommitId).build(),
                new PageRequestImpl(0, COMMITS_TO_INSPECT));

        BranchState defaultBranchState = getAggregatedStatus(commits);
        switch (defaultBranchState.state) {
            case INPROGRESS:
                mergeRequest.veto("Too many pending builds", format("REJECTED: Too many pending builds on branch %s, wait a couple of minutes and try again.", branchName));
                break;
            case FAILED:
                mergeRequest.veto("Destination branch is failed", format("REJECTED: Branch %s has at least 1 failed build for commit %s", branchName, defaultBranchState.commit));
                break;
            case UNDEFINED:
            case SUCCESSFUL:
                break;
            default:
                throw new RuntimeException();
        }
    }
}
