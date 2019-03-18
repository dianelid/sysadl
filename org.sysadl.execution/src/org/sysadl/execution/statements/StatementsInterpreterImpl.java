package org.sysadl.execution.statements;

import org.sysadl.context.SysADLContext;
import org.sysadl.context.exceptions.ContextException;
import org.sysadl.execution.engine.SysADLExecutionEngine;

import org.sysadl.BlockStatement;
import org.sysadl.DoStatement;
import org.sysadl.Expression;
import org.sysadl.ForControl;
import org.sysadl.ForStatement;
import org.sysadl.IfBlockStatement;
import org.sysadl.IfStatement;
import org.sysadl.Statement;
import org.sysadl.SwitchClause;
import org.sysadl.SwitchStatement;
import org.sysadl.VariableDecl;
import org.sysadl.WhileStatement;

public class StatementsInterpreterImpl extends SysADLStatementInterpreter {

	@Override
	public void run(BlockStatement s, SysADLContext context) throws ContextException {
		for (Object o : s.getBody()) {
			Statement a = (Statement) o;
			if(a != null) {
				run(a, context);
			}
				
		}
	}

	@Override
	public void run(VariableDecl s, SysADLContext context) throws ContextException {
		context.add(s, s.getValue());
	}

	@Override
	public void run(WhileStatement s, SysADLContext context) throws ContextException {
		SysADLExecutionEngine a = SysADLExecutionEngine.getInstance();
		Object temp = a.evaluate(s.getCondition(), context);
		while(temp instanceof Boolean && (Boolean)temp) {
			if(s.getBody() != null) {
				run(s.getBody(), context);
			}
			temp = a.evaluate(s.getCondition(), context);
		}
	
	}

	@Override
	public void run(DoStatement s, SysADLContext context) throws ContextException {
		SysADLExecutionEngine a = SysADLExecutionEngine.getInstance();
		Object temp = a.evaluate(s.getCondition(), context);
		do {
			temp = a.evaluate(s.getCondition(), context);
			if(s.getBody() != null) {
				run(s.getBody(), context);
			}
		}while(temp instanceof Boolean && (Boolean)temp);
	}

	@Override
	public void run(ForStatement s, SysADLContext context) throws ContextException {
		SysADLExecutionEngine a = SysADLExecutionEngine.getInstance();
		
		ForControl forControl = s.getControl();
		for (Object iter : forControl.getVars()) {
			if(s.getBody() != null) {
				run(s.getBody(), context);
			}
		}
		

//		// TODO Auto-generated method stub
//		VariableDecl va = (VariableDecl) s.getControl().getVars().get(0);
//		run(va,context);
//		while(true) {
//				if( a.evaluate((Expression) s.getControl().getVars().get(1), context) instanceof Boolean
//						&& ! (Boolean)a.evaluate(((Expression) s.getControl().getVars().get(1)), context) ) {
//					break;
//				}
//				run(s.getBody(), context);
//			Expression st = ((Expression) s.getControl().getVars().get(2));
//			run(st, context);
//		}
	}

	@Override
	public void run(SwitchStatement s, SysADLContext context) throws ContextException {
		SysADLExecutionEngine a = SysADLExecutionEngine.getInstance();
		Object mainExpResult = a.evaluate(s.getExpr(), context);
		boolean flag = false;
		
		for (Object o : s.getClauses()) {
			
			SwitchClause clause = (SwitchClause) o;
			
			if(mainExpResult.equals( a.evaluate(clause.getValue(),context) )) {
				flag = true;
			}
			if(flag) {
				if(clause.getBody() != null) {
					run(clause.getBody(), context);
				}
			}
		}
	}

	@Override
	public void run(IfBlockStatement s, SysADLContext context) throws ContextException {
		// Retrieve instance of ExecutionEngine
		SysADLExecutionEngine eg = SysADLExecutionEngine.getInstance();
		
		// Save if statement
		IfStatement _if = s.getMain_if();
		
		// Evaluate the condition 
		Object condition = eg.evaluate(_if.getCondition(), context);
		
		// if the condition is true
		if ((condition instanceof Boolean) && (Boolean) condition) {
			run(_if.getBody(), context);
		} else { 
			if (s.getElse()!=null) run(s.getElse().getBody(), context);
		}
	}

}
