package example;

import org.drools.core.process.instance.impl.WorkItemImpl;
import org.jbpm.bpmn2.handler.AbstractExceptionHandlingTaskHandler;
import org.jbpm.process.core.context.exception.ExceptionScope;
import org.jbpm.process.instance.ProcessInstance;
import org.jbpm.process.instance.context.exception.ExceptionScopeInstance;
import org.jbpm.process.workitem.rest.RESTWorkItemHandler;
import org.jbpm.ruleflow.instance.RuleFlowProcessInstance;
import org.jbpm.workflow.instance.NodeInstance;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.manager.RuntimeEngine;
import org.kie.api.runtime.manager.RuntimeManager;
import org.kie.api.runtime.process.WorkItem;
import org.kie.api.runtime.process.WorkItemHandler;
import org.kie.api.runtime.process.WorkItemManager;
import org.kie.internal.runtime.manager.context.ProcessInstanceIdContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessTaskHandlerDecorator extends AbstractExceptionHandlingTaskHandler {

	protected RuntimeManager runtimeManager;
	private String processId;

	private Logger log = LoggerFactory.getLogger(getClass());

	public ProcessTaskHandlerDecorator(Class<? extends WorkItemHandler> originalTaskHandlerClass,
			RuntimeManager runtimeManager) {
		super(originalTaskHandlerClass);
		this.runtimeManager = runtimeManager;
	}

	public ProcessTaskHandlerDecorator(RuntimeManager runtimeManager) {
		super(RESTWorkItemHandler.class);
		this.runtimeManager = runtimeManager;
	}

	public ProcessTaskHandlerDecorator(Class<? extends WorkItemHandler> originalTaskHandlerClass,
			RuntimeManager runtimeManager, String processId) {
		super(originalTaskHandlerClass);
		this.runtimeManager = runtimeManager;
		this.processId = processId;
	}

	@Override
	public void handleExecuteException(Throwable cause, WorkItem workItem, WorkItemManager manager) {
		log.trace("Begin");
		RuntimeEngine runtimeEngine = null;
		try {
			if (processId == null)
				processId = (String) workItem.getParameter("processId");

			log.trace(
					"Process instance id: {}, comes accross the exception {}, Starting process {} to handle the exception.",
					workItem.getProcessInstanceId(), cause, processId);

			// Create the kiesession
			runtimeEngine = runtimeManager.getRuntimeEngine(ProcessInstanceIdContext.get());
			KieSession kieSession = runtimeEngine.getKieSession();

			// Create the subprocess
			RuleFlowProcessInstance processInstance = (RuleFlowProcessInstance) kieSession
					.createProcessInstance(processId, workItem.getParameters());

			long parentInstanceId = workItem.getProcessInstanceId();
			processInstance.setMetaData("ParentProcessInstanceId", parentInstanceId);
			processInstance.setParentProcessInstanceId(parentInstanceId);
			processInstance.setDescription("Exception handling for workItemId:" + workItem.getId());

			// Start the subprocess
			kieSession.startProcessInstance(processInstance.getId());

			// Manage the subprocess completion
			if (processInstance.getState() == ProcessInstance.STATE_COMPLETED
					|| processInstance.getState() == ProcessInstance.STATE_ABORTED) {
				manager.completeWorkItem(workItem.getId(), null);
			} else {
				log.trace("Subprocess completion listner");
				ErrorHandlingCompletion errorHandlingCompletion = new ErrorHandlingCompletion(this, manager, workItem,
						cause);
				errorHandlingCompletion.listenTo(processInstance);
			}
		} catch (Throwable t) {
			log.error("Error caused by {}, handling exception: {}, in process instance id: {}", t, cause,
					workItem.getProcessInstanceId());
			throw t;
		} finally {
			if (runtimeEngine != null)
				runtimeManager.disposeRuntimeEngine(runtimeEngine);

			log.trace("End");
		}
	}

	@Override
	public void handleAbortException(Throwable cause, WorkItem workItem, WorkItemManager manager) {
		log.info("Exception while aborting work item id {} in process instance id: {}", workItem.getId(),
				workItem.getProcessInstanceId());
	}

	public void rethrowException(WorkItem workItem, Throwable cause) throws Exception {
		RuntimeEngine runtimeEngine = null;

		try {
			// retrieve the process instance of the parent process containing the failing
			// service
			runtimeEngine = runtimeManager.getRuntimeEngine(ProcessInstanceIdContext.get());
			RuleFlowProcessInstance parentProcessInstance = (RuleFlowProcessInstance) runtimeEngine.getKieSession()
					.getProcessInstance(workItem.getProcessInstanceId());

			// retrieve the node instance of the failing servise
			long nodeInstanceId = ((WorkItemImpl) workItem).getNodeInstanceId();
			NodeInstance nodeInstance = parentProcessInstance.getNodeInstance(nodeInstanceId, true);

			// throw the exception in the context of the failing services
			String faultName = cause.getClass().getName();
			ExceptionScopeInstance exceptionScopeInstance = (ExceptionScopeInstance) nodeInstance
					.resolveContextInstance(ExceptionScope.EXCEPTION_SCOPE, faultName);

			if (exceptionScopeInstance == null)
				throw new Exception("Exception Scope not found in process: " + parentProcessInstance.getProcessId());

			exceptionScopeInstance.handleException(faultName, cause);

		} finally {
			if (runtimeEngine != null)
				runtimeManager.disposeRuntimeEngine(runtimeEngine);
		}
	}

}
