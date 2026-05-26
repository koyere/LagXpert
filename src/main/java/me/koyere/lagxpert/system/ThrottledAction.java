package me.koyere.lagxpert.system;

import java.util.Map;

/**
 * ThrottledAction — safety interface for all corrective actions.
 *
 * Every system that performs automated corrective actions should implement
 * this interface to ensure safe operation: pre-condition checks, execution
 * with limits, post-action verification, and fallback on failure.
 *
 * This pattern prevents runaway corrective actions from causing more harm
 * than the problem they are trying to solve.
 */
public interface ThrottledAction {

    /**
     * Result of a corrective action execution.
     */
    class ActionResult {
        private final boolean success;
        private final int operationsPerformed;
        private final long durationMs;
        private final String errorDetail;
        private final Map<String, Object> beforeMetrics;
        private final Map<String, Object> afterMetrics;

        public ActionResult(boolean success, int operationsPerformed, long durationMs,
                            String errorDetail, Map<String, Object> beforeMetrics,
                            Map<String, Object> afterMetrics) {
            this.success = success;
            this.operationsPerformed = operationsPerformed;
            this.durationMs = durationMs;
            this.errorDetail = errorDetail;
            this.beforeMetrics = beforeMetrics;
            this.afterMetrics = afterMetrics;
        }

        public static ActionResult success(int operations, long durationMs,
                                           Map<String, Object> before, Map<String, Object> after) {
            return new ActionResult(true, operations, durationMs, null, before, after);
        }

        public static ActionResult failure(String error, long durationMs) {
            return new ActionResult(false, 0, durationMs, error, null, null);
        }

        public boolean isSuccess() { return success; }
        public int getOperationsPerformed() { return operationsPerformed; }
        public long getDurationMs() { return durationMs; }
        public String getErrorDetail() { return errorDetail; }
        public Map<String, Object> getBeforeMetrics() { return beforeMetrics; }
        public Map<String, Object> getAfterMetrics() { return afterMetrics; }
    }

    /**
     * Checks whether this system is healthy enough to perform an action.
     * Typical checks:
     *   - Is the module enabled in config?
     *   - Is the system overloaded (too many pending operations)?
     *   - Did the last action fail (cooldown before retry)?
     *   - Is the server in a state that allows this action?
     *
     * @return true if the system can safely act
     */
    boolean canAct();

    /**
     * Executes the corrective action with the given operation limit.
     * The system must never exceed maxOperations per call to prevent
     * performance impact.
     *
     * @param maxOperations Maximum number of operations to perform
     * @return ActionResult with success/failure and metrics
     */
    ActionResult execute(int maxOperations);

    /**
     * Verifies that the executed action had a positive (or at least neutral) effect.
     * Called after execute(). Compares before/after snapshots.
     *
     * @param result The result from the last execute() call
     * @return true if the action helped or was neutral
     */
    boolean verify(ActionResult result);

    /**
     * Performs a rollback or mitigation if verification failed.
     * Called when verify() returns false.
     *
     * @param result The result from the last execute() call
     */
    void fallback(ActionResult result);
}
