package example;

import java.util.Map;

import org.jbpm.ruleflow.instance.RuleFlowProcessInstance;
import org.kie.api.runtime.process.WorkItem;
import org.kie.api.runtime.process.WorkItemManager;

public class ErrorHandlingCompletion extends ProcessCompletionListener {

	private WorkItemManager manager;
	private WorkItem workItem;
	private ProcessTaskHandlerDecorator processTaskHandlerDecorator;
	private Throwable cause;

	public ErrorHandlingCompletion(ProcessTaskHandlerDecorator processTaskHandlerDecorator, WorkItemManager manager,
			WorkItem workItem, Throwable cause) {
		this.processTaskHandlerDecorator = processTaskHandlerDecorator;
		this.manager = manager;
		this.workItem = workItem;
		this.cause = cause;
	}

	@Override
	public void processCompleted(RuleFlowProcessInstance processInstance) {
		// retry is a variable used in the exception handling process to catch the
		// willingness of retring the failing service
		Boolean retry = (Boolean) processInstance.getVariable("retry");
		if (retry != null && retry == true) {
			// update the parameters with the values coming from the exception handling
			// process
			Map<String, Object> parameters = workItem.getParameters();
			for (String key : parameters.keySet()) {
				Object value = processInstance.getVariable(key);
				if (value != null)
					parameters.put(key, value);
			}

			// execute again the original WIH
			processTaskHandlerDecorator.executeWorkItem(workItem, manager);
		}
		// user ask to skip the failing service
		else {
			// mark workitem aborted (skipped)
			manager.abortWorkItem(workItem.getId());
		}
	}

	@Override
	public void processAborted(RuleFlowProcessInstance processInstance) {
		try {
			processTaskHandlerDecorator.rethrowException(workItem, cause);

			// mark the work item failed and call the abort procedure
			manager.abortWorkItem(workItem.getId());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
