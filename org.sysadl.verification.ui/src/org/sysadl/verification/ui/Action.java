package org.sysadl.verification.ui;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.ocl.OCL;
import org.eclipse.ocl.ParserException;
import org.eclipse.ocl.ecore.Constraint;
import org.eclipse.ocl.ecore.EcoreEnvironmentFactory;
import org.eclipse.ocl.expressions.OCLExpression;
import org.eclipse.ocl.helper.OCLHelper;
import org.eclipse.ocl.options.ParsingOptions;
import org.eclipse.sirius.diagram.DNode;
import org.eclipse.sirius.diagram.business.internal.metamodel.spec.DNodeListSpec;
import org.eclipse.sirius.tools.api.ui.IExternalJavaAction;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;
import org.sysadl.ComponentDef;
import org.sysadl.Invariant;
import org.sysadl.Style;
import org.sysadl.SysADLPackage;

/**
 * 
 * @author Eduardo Silva
 *
 */
public class Action implements IExternalJavaAction {
	private final String CONSOLE_NAME = "SysADL";
	OCL<?, EClassifier, ?, ?, ?, ?, ?, ?, ?, Constraint, EClass, EObject> ocl;
	OCLHelper<EClassifier, ?, ?, Constraint> helper;

	public Action() {
		// TODO Auto-generated constructor stub
	}

	@Override
	public boolean canExecute(Collection<? extends EObject> arg0) {
		// Can only execute on ComponentDefs or ArchitectureDefs
		/*
		 * for (EObject e : arg0) { if (e instanceof ComponentDef) { // ArchitectureDef
		 * is also a ComponentDef continue; } else return false; }
		 */
		return true;
	}

	@Override
	public void execute(Collection<? extends EObject> arg0, Map<String, Object> arg1) {
		DNodeListSpec d = (DNodeListSpec) arg1.get("component");
		try {
			ComponentDef c = (ComponentDef) d.getTarget();
			checkComponent(c);
		} catch (Exception e) {
			print("[ERROR] Some error occurred, unable to start verification");
		}
	}

	/**
	 * Checks the invariants of a specific component
	 * 
	 * @param c the component to be verified
	 */
	private void checkComponent(ComponentDef c) {
		try {
			setupOCL();
		} catch (ParserException e1) {
			print(e1.getMessage());
		}

		print("Starting Verification of Component " + c.getName() + "...");
		List<Style> abs = c.getAppliedStyle();
		if (abs == null || abs.isEmpty()) {
			print("Component " + c.getName() + " does not follow any style. Verification aborted.");
			return;
		}

		for (Style s : abs) {
			int total = s.getInvariants().size();
			int violations = 0;
			boolean isValid;
			print("Verification [" + c.getName() + "]: Checking Style " + s.getName());
			print("Number of Invariants: " + s.getInvariants().size());
			for (Invariant i : s.getInvariants()) {
				try {
					// for debugging
					if (i.getExpr() != null && i.getExpr().startsWith("debug:")) {
						Object value = debugInvariant(c, i.getExpr().substring(6));
						print("[DEBUG] " + i.getName());
						print("[DEBUG]: " + value);
						continue;
					}
					// else
					print("\n" + "Verification [" + c.getName() + "]: Checking invariant " + i.getName());
					isValid = checkInvariant(c, i.getExpr());
					if (!isValid)
						violations++;
					print("Invariant " + i.getName() + (i.getExpr() == null ? ": " : " (" + i.getExpr() + "): ")
							+ (isValid ? "valid" : "violated"));
				} catch (ParserException e) {
					print("[ERROR] Invariant " + i.getName() + " has shown an error: " + e.getMessage());
				}

			}
			print("Verification [" + c.getName() + "]: End verification of style " + s.getName());
			print("Invariants: " + total + " (total); " + violations + " (violated)");
			print(((total - violations) / total) * 100 + "% constraints validated");
		}
	}

	/**
	 * Sets up the OCL environment for verification Initialize attributes ocl and
	 * helper, and define the functions may be used by the invariants
	 * 
	 * @throws ParserException when there is a syntax error in any function
	 */
	private void setupOCL() throws ParserException {
		ocl = OCL.newInstance(EcoreEnvironmentFactory.INSTANCE);
		ParsingOptions.setOption(ocl.getEnvironment(), ParsingOptions.implicitRootClass(ocl.getEnvironment()),
				EcorePackage.Literals.EOBJECT);

		helper = ocl.createOCLHelper();
		helper.setContext(SysADLPackage.Literals.CONFIGURATION);

		helper.defineOperation(
				"checkPortUseAbstractComponent(portUse : PortUse, abstractComponent : String) : Boolean = "
						+ "let abs : AbstractComponentDef = portUse.eContainer().oclAsType(ComponentUse).definition.abstractComponent in "
						+ "(not abs.oclIsUndefined()) and abs.name = abstractComponent");

		helper.defineOperation(
				"checkCPRecursive(configuration : Configuration, abstractComponent : String) : Boolean =  "
						+ "if configuration.components->exists(cp | (not cp.definition.abstractComponent.oclIsUndefined()) and cp.definition.abstractComponent.name = abstractComponent) then "
						+ "true " + "else "
						+ "configuration.components->exists(cp | (not cp.definition.composite.oclIsUndefined()) and self.checkCPRecursive(cp.definition.composite, abstractComponent)) "
						+ "endif");

		helper.defineOperation(
				"checkCNRecursive(configuration : Configuration, abstractConnector : String) : Boolean =	"
						+ "if configuration.connectors->exists(cn | (not cn.definition.abstractConnector.oclIsUndefined()) and cn.definition.abstractConnector.name = abstractConnector) then "
						+ "true " + "else "
						+ "configuration.components->exists(cp | (not cp.definition.composite.oclIsUndefined()) and self.checkCNRecursive(cp.definition.composite, abstractConnector)) or "
						+ "configuration.connectors->exists(cn | (not cn.definition.composite.oclIsUndefined()) and self.checkCNRecursive(cn.definition.composite, abstractConnector)) "
						+ "endif");

		helper.defineOperation("checkPTRecursive(configuration : Configuration, abstractPort : String) : Boolean = "
				+ "if configuration.components->exists(cp | cp.ports->exists(pt | (not pt.abstractPort.oclIsUndefined()) and pt.abstractPort.name = abstractPort)) then "
				+ "true " + "else "
				+ "configuration.components->exists(cp | (not cp.definition.composite.oclIsUndefined()) and self.checkPTRecursive(cp.definition.composite, abstractPort)) "
				+ "endif");

		helper.defineOperation("ControllerCPEmbedded(configuration : Configuration) : Boolean = "
				+ "let cpDef : ComponentDef = configuration.eContainer().oclAsType(ComponentDef) in "
				+ "((not cpDef.abstractComponent.oclIsUndefined()) and cpDef.abstractComponent.name = 'DeviceCP') or "
				+ "(not configuration.components->exists(cp | (not cp.definition.abstractComponent.oclIsUndefined()) and cp.definition.abstractComponent.name = 'ControllerCP')) "
				+ "and "
				+ "configuration.components->forAll(cp | (not cp.definition.composite.oclIsUndefined()) implies self.ControllerCPEmbedded(cp.definition.composite))");

		helper.defineOperation(
				"checkBindingsRecursive(configuration : Configuration, abstractConnector : String) : Boolean = "
						+ "let cnStyle : OrderedSet(ConnectorUse) = configuration.connectors->select(cn | (not cn.definition.abstractConnector.oclIsUndefined()) and cn.definition.abstractConnector.name = abstractConnector) in "
						+ "(cnStyle->isEmpty() or cnStyle->forAll(cnUse | cnUse.bindings->size()=1)) " + "and "
						+ "configuration.components->forAll(cp | (not cp.definition.composite.oclIsUndefined()) implies self.checkBindingsRecursive(cp.definition.composite, abstractConnector)) "
						+ "and "
						+ "configuration.connectors->forAll(cn | (not cn.definition.composite.oclIsUndefined()) implies self.checkBindingsRecursive(cn.definition.composite, abstractConnector))");

		helper.defineOperation("SensorConnection(configuration : Configuration) : Boolean = "
				+ "let cpSensors : OrderedSet(ComponentUse) = configuration.components->select(cp | (not cp.definition.abstractComponent.oclIsUndefined()) and cp.definition.abstractComponent.name = 'SensorCP') in "
				+ "(cpSensors->isEmpty() or cpSensors->forAll(sensorCP | "
				+ "configuration.connectors->exists(cn |(self.checkPortUseAbstractComponent(cn.bindings->first().destination, 'DeviceCP') or "
				+ "self.checkPortUseAbstractComponent(cn.bindings->first().destination, 'ControllerCP')) and "
				+ "sensorCP.ports->exists(p | p.name = cn.bindings->first().source.name))))" + "and "
				+ "configuration.components->forAll(cp | (not cp.definition.composite.oclIsUndefined()) implies self.SensorConnection(cp.definition.composite))");

		helper.defineOperation("ActuatorConnection(configuration : Configuration) : Boolean = "
				+ "let cpActuators : OrderedSet(ComponentUse) = configuration.components->select(cp | (not cp.definition.abstractComponent.oclIsUndefined()) and cp.definition.abstractComponent.name = 'ActuatorCP') in "
				+ "(cpActuators->isEmpty() or cpActuators->forAll(actuatorCP | "
				+ "configuration.connectors->exists(cn |(self.checkPortUseAbstractComponent(cn.bindings->first().source, 'DeviceCP') or "
				+ "self.checkPortUseAbstractComponent(cn.bindings->first().source, 'ControllerCP')) and "
				+ "actuatorCP.ports->exists(p | p.name = cn.bindings->first().destination.name))))" + "and "
				+ "configuration.components->forAll(cp | (not cp.definition.composite.oclIsUndefined()) implies self.ActuatorConnection(cp.definition.composite))");

		helper.defineOperation("Communication(configuration : Configuration) : Boolean = "
				+ "configuration.connectors->forAll(cn | cn.bindings->forAll(b | "
				+ "let sourceSensor : Boolean = self.checkPortUseAbstractComponent(b.source, 'SensorCP') in "
				+ "let sourceActuator : Boolean = self.checkPortUseAbstractComponent(b.source, 'ActuatorCP') in "
				+ "let destinationSensor : Boolean = self.checkPortUseAbstractComponent(b.destination, 'SensorCP') in "
				+ "let destinationActuator : Boolean = self.checkPortUseAbstractComponent(b.destination, 'ActuatorCP') in "
				+ "not ((sourceSensor and destinationActuator) or (sourceActuator and destinationSensor))" + ")) "
				+ "and "
				+ "configuration.components->forAll(cp | (not cp.definition.composite.oclIsUndefined()) implies self.Communication(cp.definition.composite))");

		helper.defineOperation("ExistsConnectionForAll(src : String, tar : String) : Boolean = "
				+ "let cps : OrderedSet(ComponentUse) = self.components->select(cp | (not cp.definition.abstractComponent.oclIsUndefined()) and cp.definition.abstractComponent.name = src) in "
				+ "(cps->isEmpty() or cps->forAll(cp | self.connectors->exists(cn |self.checkPortUseAbstractComponent(cn.bindings->first().destination, tar) and cp.ports->exists(p | p.name = cn.bindings->first().source.name))))");

		helper.defineOperation("ExistsConnection(src : String, tar : String) : Boolean = "
				+ "let cps : OrderedSet(ComponentUse) = self.components->select(cp | (not cp.definition.abstractComponent.oclIsUndefined()) and cp.definition.abstractComponent.name = src) in "
				+ "(cps->isEmpty() or cps->exists(cp | self.connectors->exists(cn |self.checkPortUseAbstractComponent(cn.bindings->first().destination, tar) and cp.ports->exists(p | p.name = cn.bindings->first().source.name))))");
	}

	/**
	 * Check a specific invariant
	 * 
	 * @param c    the component that will be checked
	 * @param expr the invariant expression
	 * @return false when the invariant is violated, true otherwise
	 * @throws ParserException when there is a syntax error in the invariant
	 */
	private boolean checkInvariant(ComponentDef c, String expr) throws ParserException {
		if (expr == null || expr.isEmpty())
			return true;

		Constraint query = helper.createInvariant(expr);
		return ocl.check(c.getComposite(), query);
	}

	private Object debugInvariant(ComponentDef c, String expr) throws ParserException {
		if (expr == null || expr.isEmpty())
			return true;

		OCLExpression exp = helper.createQuery(expr);
		Object value = ocl.evaluate(c.getComposite(), exp);
		return value;
	}

	private MessageConsole findConsole(String name) {
		ConsolePlugin plugin = ConsolePlugin.getDefault();
		IConsoleManager conMan = plugin.getConsoleManager();
		IConsole[] existing = conMan.getConsoles();
		for (int i = 0; i < existing.length; i++)
			if (name.equals(existing[i].getName()))
				return (MessageConsole) existing[i];
		// no console found, so create a new one
		MessageConsole myConsole = new MessageConsole(name, null);
		conMan.addConsoles(new IConsole[] { myConsole });
		return myConsole;
	}

	private void print(String string) {
		MessageConsole myConsole = findConsole(CONSOLE_NAME);
		MessageConsoleStream out = myConsole.newMessageStream();
		out.println(string);
		System.out.println(string); // for development purposes
	}

}
