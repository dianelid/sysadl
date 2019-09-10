package org.sysadl.verification.ui;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.ocl.OCL;
import org.eclipse.ocl.ParserException;
import org.eclipse.ocl.Query;
import org.eclipse.ocl.ecore.Constraint;
import org.eclipse.ocl.ecore.EcoreEnvironmentFactory;
import org.eclipse.ocl.expressions.OCLExpression;
import org.eclipse.ocl.helper.OCLHelper;
import org.eclipse.ocl.options.ParsingOptions;
import org.eclipse.sirius.diagram.business.internal.metamodel.spec.DNodeListSpec;
import org.eclipse.sirius.tools.api.ui.IExternalJavaAction;
import org.sysadl.AbstractComponentDef;
import org.sysadl.ComponentDef;
import org.sysadl.Configuration;
import org.sysadl.Invariant;
import org.sysadl.Style;
import org.sysadl.SysADLPackage;

/**
 * 
 * @author Eduardo Silva
 *
 */
public class Action implements IExternalJavaAction {
	OCL<?, EClassifier, ?, ?, ?, ?, ?, ?, ?, Constraint, EClass, EObject> ocl;
	OCLHelper<EClassifier, ?, ?, Constraint> helper;
	
	public Action() {
		// TODO Auto-generated constructor stub
	}

	@Override
	public boolean canExecute(Collection<? extends EObject> arg0) {
		// TODO Can only execute on ComponentDefs or ArchitectureDefs
		return true;
	}

	@Override
	public void execute(Collection<? extends EObject> arg0, Map<String, Object> arg1) {
		DNodeListSpec d = (DNodeListSpec) arg1.get("component");
		ComponentDef c = (ComponentDef) d.getTarget();
		
		checkComponent(c);
	}

	/**
	 * Checks the invariants of a specific component
	 * @param c the component to be verified
	 */
	private void checkComponent(ComponentDef c) {
		try {
			setupOCL();
		} catch (ParserException e1) {
			// TODO Auto-generated catch block
			System.out.println(e1.getMessage());
		}
		
		System.out.println("Starting Verification of Component "+c.getName()+"...");
		List<Style> abs = c.getAppliedStyle();
		if (abs==null || abs.isEmpty()) {
			System.out.println("Component "+c.getName()+" does not follow any style. Verification aborted.");
			return;
		}
		
		for (Style s : abs) {
			int total = s.getInvariants().size();
			int violations = 0;
			boolean isValid;
			System.out.println("Verification ["+c.getName()+"]: Checking Style "+s.getName());
			System.out.println("Number of Invariants: "+s.getInvariants().size());
			for (Invariant i : s.getInvariants()) {
				System.out.println("\n"+"Verification ["+c.getName()+"]: Checking invariant "+i.getName());
				try {
					isValid = checkInvariant(c,i.getExpr());
					if (!isValid) violations++;
					System.out.println("Invariant "+i.getName()+(i.getExpr()==null? ": " : " ("+i.getExpr()+"): ")+(isValid? "valid" : "violated"));
				} catch (ParserException e) {
					System.out.println("[ERROR] Invariant "+i.getName()+ " has shown an error: "+e.getMessage());
				}

			}
			System.out.println("Verification ["+c.getName()+"]: End verification of style "+s.getName());
			System.out.println("Invariants: "+total+" (total); "+violations+" (violated)");
			System.out.println(((total-violations)/total)*100+"% constraints validated");
		}
	}

	/**
	 * Sets up the OCL environment for verification
	 * Initialize attributes ocl and helper, and define the functions may be used by the invariants
	 * @throws ParserException when there is a syntax error in any function
	 */
	private void setupOCL() throws ParserException {
	    ocl = OCL.newInstance(EcoreEnvironmentFactory.INSTANCE);
	    ParsingOptions.setOption(ocl.getEnvironment(),
	    	    ParsingOptions.implicitRootClass(ocl.getEnvironment()),
	    	    EcorePackage.Literals.EOBJECT);
		helper = ocl.createOCLHelper();
		helper.setContext(SysADLPackage.Literals.CONFIGURATION);
		
	    helper.defineOperation("checkPortUseAbstractComponent(portUse : PortUse, abstractComponent : String) : Boolean = " + 
	    		"self.components->select(cp | cp.definition.abstractComponent.name = abstractComponent)->" + 
	    		"collect(cpUseSensor | cpUseSensor.ports)->exists(p | p = portUse)");
	    
	    
	    helper.defineOperation("checkCPRecursive(configuration : Configuration, abstractComponent : String) : Boolean =  "
	    		+ "if configuration.components->exists(cp | (not cp.definition.abstractComponent.oclIsUndefined()) and cp.definition.abstractComponent.name = abstractComponent) then "
	    		+ "true "
	    		+ "else "
	    		+ "configuration.components->exists(cp | (not cp.definition.composite.oclIsUndefined()) and self.checkCPRecursive(cp.definition.composite, abstractComponent)) "
	    		+ "endif");
	    
	    helper.defineOperation("checkCNRecursive(configuration : Configuration, abstractConnector : String) : Boolean =	"
	    		+ "if configuration.connectors->exists(cn | (not cn.definition.abstractConnector.oclIsUndefined()) and cn.definition.abstractConnector.name = abstractConnector) then "
	    		+ "true "
	    		+ "else "
	    		+ "configuration.components->exists(cp | (not cp.definition.composite.oclIsUndefined()) and self.checkCNRecursive(cp.definition.composite, abstractConnector)) or "
	    		+ "configuration.connectors->exists(cn | (not cn.definition.composite.oclIsUndefined()) and self.checkCNRecursive(cn.definition.composite, abstractConnector)) "
	    		+ "endif");
	    
	    helper.defineOperation("checkPTRecursive(configuration : Configuration, abstractPort : String) : Boolean = "
	    		+ "if configuration.components->exists(cp | cp.ports->exists(pt | (not pt.abstractPort.oclIsUndefined()) and pt.abstractPort.name = abstractPort)) then "
	    		+ "true "
	    		+ "else "
	    		+ "configuration.components->exists(cp | (not cp.definition.composite.oclIsUndefined()) and self.checkPTRecursive(cp.definition.composite, abstractPort)) "
	    		+ "endif");	    
	    
	    helper.defineOperation("checkBindingsRecursive(configuration : Configuration, abstractConnector : String) : Boolean = "
	    		+ "(not (configuration.connectors->select(cn | (not cn.definition.abstractConnector.oclIsUndefined()) and cn.definition.abstractConnector.name = abstractConnector)->exists(cn1 | cn1.bindings->size()>1))) and "
	    		+ "configuration.components->forAll(cp | (not cp.definition.composite.oclIsUndefined()) implies self.checkBindingsRecursive(cp.definition.composite, abstractConnector)) and "
	    		+ "configuration.connectors->forAll(cn | (not cn.definition.composite.oclIsUndefined()) implies self.checkBindingsRecursive(cn.definition.composite, abstractConnector))");
	   
	}

	/**
	 * Check a specific invariant
	 * @param c the component that will be checked
	 * @param expr the invariant expression
	 * @return false when the invariant is violated, true otherwise
	 * @throws ParserException when there is a syntax error in the invariant
	 */
	private boolean checkInvariant(ComponentDef c, String expr) throws ParserException {
		if (expr==null || expr.isEmpty()) return true;
		
	    Constraint query = helper.createInvariant(expr);
		return ocl.check(c.getComposite(), query);
	}

}
