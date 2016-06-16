package com.risingoak.stash.plugins.hook;


import com.atlassian.bitbucket.build.BuildState;
import com.atlassian.bitbucket.build.BuildStatus;
import com.atlassian.bitbucket.build.BuildStatusService;
import com.atlassian.bitbucket.commit.Commit;
import com.atlassian.bitbucket.commit.CommitService;
import com.atlassian.bitbucket.repository.RefService;
import com.atlassian.bitbucket.util.Page;

public class AbstractRejectHook {

    public static final int COMMITS_TO_INSPECT = 10;

    protected RefService refService;
    protected BuildStatusService buildStatusService;
    protected CommitService commitService;

    public AbstractRejectHook(RefService refService, CommitService commitService, BuildStatusService buildStatusService) {
        this.refService = refService;
        this.commitService = commitService;
        this.buildStatusService = buildStatusService;
    }

    protected BuildState0 getAggregatedStatus(String theHash) {
        boolean hasPending = false;
        boolean hasSuccess = false;
        for (BuildStatus status : buildStatusService.findAll(theHash).getValues()) {
            if (BuildState.FAILED == status.getState()) {
                return BuildState0.FAILED;
            } else if (status.getState() == BuildState.INPROGRESS) {
                hasPending = true;
            } else if (status.getState() == BuildState.SUCCESSFUL) {
                hasSuccess = true;
            }
        }

        if (hasPending) {
            return BuildState0.INPROGRESS;
        }
        if (hasSuccess) {
            return BuildState0.SUCCESSFUL;
        }
        return BuildState0.UNDEFINED;
    }

    protected BranchState getAggregatedStatus(Page<Commit> commits) {
        boolean hasPending = false;
        for (Commit commit : commits.getValues()) {
            BuildState0 aggregatedStatus = getAggregatedStatus(commit.getId());
            switch (aggregatedStatus) {
                case UNDEFINED:
                    continue;
                case SUCCESSFUL:
                    return new BranchState(BuildState0.SUCCESSFUL);
                case FAILED:
                    return new BranchState(BuildState0.FAILED, commit.getDisplayId());
                case INPROGRESS:
                    hasPending = true;
                    break;
            }
        }
        if (hasPending) {
            return new BranchState(BuildState0.INPROGRESS);
        }
        return new BranchState(BuildState0.UNDEFINED);
    }

    protected static class BranchState {
        protected final BuildState0 state;
        protected final String commit;

        public BranchState(BuildState0 state) {
            this.state = state;
            this.commit = null;
        }

        public BranchState(BuildState0 state, String commit) {
            this.state = state;
            this.commit = commit;
        }
    }

    public enum BuildState0 {UNDEFINED, SUCCESSFUL, FAILED, INPROGRESS}
}