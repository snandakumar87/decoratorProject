package example;

import org.jbpm.process.instance.ProcessInstance;
import org.jbpm.ruleflow.instance.RuleFlowProcessInstance;
import org.kie.api.event.process.DefaultProcessEventListener;
import org.kie.api.event.process.ProcessCompletedEvent;

public abstract class ProcessCompletionListener extends DefaultProcessEventListener {

	private long listeningId;

	public ProcessCompletionListener() {
	}

	public void listenTo(RuleFlowProcessInstance processInstance) {
		listeningId = processInstance.getId();
		processInstance.getKnowledgeRuntime().addEventListener(this);
	}

	private void stopListening(RuleFlowProcessInstance processInstance) {
		processInstance.getKnowledgeRuntime().removeEventListener(this);
	}
	
	@Override
	public void afterProcessCompleted(ProcessCompletedEvent event) {
		if (event.getProcessInstance().getId() != listeningId)
			return;
			
		if (event.getProcessInstance() instanceof RuleFlowProcessInstance) {
			RuleFlowProcessInstance processInstance = (RuleFlowProcessInstance) event.getProcessInstance();

			if (processInstance.getState() == ProcessInstance.STATE_COMPLETED) {
				processCompleted(processInstance);
			} else {
				processAborted(processInstance);
			}
			stopListening(processInstance);
		} else {
			System.err.format("event: %s with wrong process instance", event);
		}
	}

	public abstract void processCompleted(RuleFlowProcessInstance processInstance);

	public abstract void processAborted(RuleFlowProcessInstance processInstance);

}
