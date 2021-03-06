package edu.isi.wings.execution.engine.api;

import edu.isi.wings.execution.engine.classes.RuntimePlan;
import edu.isi.wings.execution.logger.api.ExecutionLoggerAPI;

public interface PlanExecutionEngine {

	public void execute(RuntimePlan exe);
	
	public void onStepEnd(RuntimePlan exe);

	public void abort(RuntimePlan exe);
	
	public int getMaxParallelSteps();
	
	public void setMaxParallelSteps(int num);
	
	public void setStepExecutionEngine(StepExecutionEngine engine);
	
	public StepExecutionEngine getStepExecutionEngine();
	
	public void setExecutionLogger(ExecutionLoggerAPI monitor);
	
	public ExecutionLoggerAPI getExecutionLogger();
}
