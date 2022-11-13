package core.alloy.codegen;

import core.exceptions.GenerationException;
import core.Global;
import core.models.declare.data.NumericDataImpl;
import declare.DeclareParserException;
import declare.lang.DataConstraint;
import declare.lang.Statement;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by Vasiliy on 2017-10-23.
 */
public class DataConstraintGenerator {

    boolean vacuity;
    Map<String, NumericDataImpl> map;
    StringBuilder alloy;
    FunctionGenerator fnGen;
    Set<String> supported = Global.getAlloySupportedConstraints();
    List<Pair<Statement, String>> alloyConstraints;

    public DataConstraintGenerator(int maxSameInstances, int bitwidth, boolean vacuity) {
        this.vacuity = vacuity;
        this.fnGen = new FunctionGenerator(maxSameInstances, bitwidth);
    }

    public String Generate(DataConstraint c, String name, Map<String, NumericDataImpl> map, List<Pair<Statement, String>> alloyConstraints)
            throws DeclareParserException, GenerationException {
        this.map = map;
        this.alloy = new StringBuilder();
        this.alloyConstraints = alloyConstraints;

        switch(c.getName()) {
        case "Init":
        	addInitDataConstraint(c, name);
        	break;
        case "End":
        	addEndDataConstraint(c, name);
        	break;
        case "Absence":
        	addAbsenceDataConstraint(c, name, Integer.parseInt(c.taskB()));
        	break;
        case "Existence":
            addExistenceDataConstraint(c, name, Integer.parseInt(c.taskB()));
            break;
        case "Exactly":
        	addExactlyDataConstraint(c, name, Integer.parseInt(c.taskB()));
        	break;
        case "RespondedExistence":
        	addRespondedExistenceDataConstraint(c, name, name + "c");
        	break;
        case "CoExistence":
        	addCoExistenceDataConstraint(c, name, name + "c");
        	break;
        case "Response":
        	addResponseDataConstraint(c, name, name + "c");
        	break;
        case "AlternateResponse":
        	addAlternateResponseDataConstraint(c, name, name + "c");
        	break;
        case "ChainResponse":
        	addChainResponseDataConstraint(c, name, name + "c");
        	break;
        case "Precedence":
        	addPrecedenceDataConstraint(c, name, name + "c");
        	break;
        case "AlternatePrecedence":
        	addAlternatePrecedenceDataConstraint(c, name, name + "c");
        	break;
        case "ChainPrecedence":
        	addChainPrecedenceDataConstraint(c, name, name + "c");
        	break;
        case "Succession":
        	addSuccessionDataConstraint(c, name, name + "c");
        	break;
        case "AlternateSuccession":
        	addAlternateSuccessionDataConstraint(c, name, name + "c");
        	break;
        case "ChainSuccession":
        	addChainSuccessionDataConstraint(c, name, name + "c");
        	break;
        case "NotRespondedExistence":
        	addNotRespondedExistenceDataConstraint(c, name, name + "c");
        	break;
        case "NotResponse":
        	addNotResponseDataConstraint(c, name, name + "c");
        	break;
        case "NotPrecedence":
        	addNotPrecedenceDataConstraint(c, name, name + "c");
        	break;
        case "NotChainResponse":
        	addNotChainResponseDataConstraint(c, name, name + "c");
        	break;
        case "NotChainPrecedence":
        	addNotChainPrecedenceDataConstraint(c, name, name + "c");
        	break;
        case "Choice":
        	addChoiceDataConstraint(c, name, name + "a");
        	break;
        case "ExclusiveChoice":
        	addExclusiveChoiceDataConstraint(c, name, name + "a");
        	break;
        	
        default:
        	throw new DeclareParserException("Constraint '" + c.getName() + "' is not supported. Supported constraints are: " + String.join(", ", supported));
        }

        if (vacuity && c.supportsVacuity()) {
            alloy.append("fact { // vacuity").append(System.lineSeparator())
            		.append("\tsome te: Event | te.task = ").append(c.taskA()).append(" and ").append(name).append("[te]").append(System.lineSeparator())
            		.append("}").append(System.lineSeparator());
        }

        return alloy.toString();
    }

    private void addInitDataConstraint(DataConstraint one, String name) throws DeclareParserException, GenerationException {
        alloyConstraints.add(
        		Pair.of(one.getStatement(), 
        				String.format("%s = TE0.task and  %s[TE0]", one.taskA(), name)
        		)
        );
        alloy.append( fnGen.generateFunction(name, one.getFirstFunction(), map, one.getArgs()) );
    }
    
    private void addEndDataConstraint(DataConstraint one, String name) throws DeclareParserException, GenerationException {
    	alloyConstraints.add(
        		Pair.of(one.getStatement(), 
        				String.format("some te: Event | (%s = te.task and %s[te]) and no fte: Event | Next[te, fte]", one.taskA(), name)
        		)
        );
        alloy.append( fnGen.generateFunction(name, one.getFirstFunction(), map, one.getArgs()) );
    }

    private void addExistenceDataConstraint(DataConstraint one, String fnName, int n) throws DeclareParserException, GenerationException {
    	/*if (n == 1)
            alloyConstraints.add(Pair.of(one.getStatement(),String.format("some te: Event | te.task = %s and %s[te]", one.taskA(), fnName)));
    	else*/
    		alloyConstraints.add(
    				Pair.of(one.getStatement(), 
    						String.format("#{ te: Event | %s  = te.task and %s[te]} >= %d", one.taskA(), fnName, n)
    				)
    		);
        
    	alloy.append( fnGen.generateFunction(fnName, one.getFirstFunction(), map, one.getArgs()) );
    }

    private void addAbsenceDataConstraint(DataConstraint one, String fnName, int n) throws DeclareParserException, GenerationException {
        /*if (n == 1)
            alloyConstraints.add(Pair.of(one.getStatement(),String.format("no te: Event | te.task = %s and %s[te]", one.taskA(), fnName)));
        else*/
        	alloyConstraints.add(
        			Pair.of(one.getStatement(), 
        					String.format("#{ te: Event | te.task = %s and %s[te]} < %d", one.taskA(), fnName, n)
        			)
        	);
        
        alloy.append( fnGen.generateFunction(fnName, one.getFirstFunction(), map, one.getArgs()) );
    }

    private void addExactlyDataConstraint(DataConstraint one, String fnName, int n) throws DeclareParserException, GenerationException {
        alloyConstraints.add(
        		Pair.of(one.getStatement(), 
        				String.format("#{ te: Event | te.task = %s and %s[te]} = %d", one.taskA(), fnName, n)
        		)
        );
        alloy.append( fnGen.generateFunction(fnName, one.getFirstFunction(), map, one.getArgs()) );
    }

    private void addRespondedExistenceDataConstraint(DataConstraint one, String fFnName, String sFnName) throws DeclareParserException, GenerationException {
        alloyConstraints.add(
        		Pair.of(one.getStatement(), 
        				String.format("%s(some ote: Event | %s = ote.task and %s[te, ote])", getActivation(one, fFnName), one.taskB(), sFnName)
        		)
        );
        alloy.append( fnGen.generateFunction(fFnName, one.getFirstFunction(), map, one.getArgs()) );
        alloy.append( fnGen.generateFunction(sFnName, one.getSecondFunction(), map, one.getArgs()) );
    }
    
    private void addCoExistenceDataConstraint(DataConstraint one, String fFnName, String sFnName) throws DeclareParserException, GenerationException {
    	// CoExistence[A, B] = RespondedExistence[A, B] and RespondedExistence[B, A]
    	String respEx = String.format("%s(some ote: Event | %s = ote.task and %s[te, ote])", getActivation(one, fFnName), one.taskB(), sFnName);
    	String reverseRespEx = String.format("all te: Event | %s = te.task implies (some ote: Event | %s = ote.task and %s[ote, te] and %s[ote])", one.taskB(), one.taskA(), sFnName, fFnName);
    	String coEx = "(" + respEx + ") and (" + reverseRespEx + ")";
    	
    	alloyConstraints.add( Pair.of(one.getStatement(), coEx) );
        alloy.append( fnGen.generateFunction(fFnName, one.getFirstFunction(), map, one.getArgs()) );
        alloy.append( fnGen.generateFunction(sFnName, one.getSecondFunction(), map, one.getArgs()) );
    }

    private void addResponseDataConstraint(DataConstraint one, String fFnName, String sFnName) throws DeclareParserException, GenerationException {
        alloyConstraints.add(
        		Pair.of(one.getStatement(),
        				String.format("%s(some fte: Event | %s = fte.task and %s[te, fte] and After[te, fte])", getActivation(one, fFnName), one.taskB(), sFnName)
        		)
        );
        alloy.append( fnGen.generateFunction(fFnName, one.getFirstFunction(), map, one.getArgs()) );
        alloy.append( fnGen.generateFunction(sFnName, one.getSecondFunction(), map, one.getArgs()) );
    }

    private void addAlternateResponseDataConstraint(DataConstraint one, String fFnName, String sFnName) throws DeclareParserException, GenerationException {
        alloyConstraints.add(
        		Pair.of(one.getStatement(),
        				String.format("%s(some fte: Event | %s = fte.task and %s[te, fte] and After[te, fte] and "
        								+ "(no ite: Event | %s = ite.task and %s[ite] and  After[te, ite] and After[ite, fte]))", getActivation(one, fFnName), one.taskB(), sFnName, one.taskA(), fFnName)
        		)
        );
        alloy.append( fnGen.generateFunction(fFnName, one.getFirstFunction(), map, one.getArgs()) );
        alloy.append( fnGen.generateFunction(sFnName, one.getSecondFunction(), map, one.getArgs()) );
    }

    private void addChainResponseDataConstraint(DataConstraint one, String fFnName, String sFnName) throws DeclareParserException, GenerationException {
        alloyConstraints.add(
        		Pair.of(one.getStatement(),
        				String.format("%s(some fte: Event | %s = fte.task and Next[te, fte] and %s[te, fte])", getActivation(one, fFnName), one.taskB(), sFnName)
        		)
        );
        alloy.append( fnGen.generateFunction(fFnName, one.getFirstFunction(), map, one.getArgs()) );
        alloy.append( fnGen.generateFunction(sFnName, one.getSecondFunction(), map, one.getArgs()) );
    }

    private void addPrecedenceDataConstraint(DataConstraint one, String fFnName, String sFnName) throws DeclareParserException, GenerationException {
        alloyConstraints.add(
        		Pair.of(one.getStatement(),
        				String.format("%s(some fte: Event | %s = fte.task and %s[te, fte] and After[fte, te])", getActivation(one, fFnName), one.taskB(), sFnName)
        		)
        );
        alloy.append( fnGen.generateFunction(fFnName, one.getFirstFunction(), map, one.getArgs()) );
        alloy.append( fnGen.generateFunction(sFnName, one.getSecondFunction(), map, one.getArgs()) );
    }

    private void addAlternatePrecedenceDataConstraint(DataConstraint one, String fFnName, String sFnName) throws DeclareParserException, GenerationException {
        alloyConstraints.add(
        		Pair.of(one.getStatement(),
        				String.format("%s(some fte: Event | %s = fte.task and %s[te, fte] and After[fte, te] and "
        								+ "(no ite: Event | %s = ite.task and %s[ite] and After[fte, ite] and After[ite, te]))", getActivation(one, fFnName), one.taskB(), sFnName, one.taskA(), fFnName)
        		)
        );
        alloy.append( fnGen.generateFunction(fFnName, one.getFirstFunction(), map, one.getArgs()) );
        alloy.append( fnGen.generateFunction(sFnName, one.getSecondFunction(), map, one.getArgs()) );
    }

    private void addChainPrecedenceDataConstraint(DataConstraint one, String fFnName, String sFnName) throws DeclareParserException, GenerationException {
        alloyConstraints.add(
        		Pair.of(one.getStatement(),
        				String.format("%s(some fte: Event | %s = fte.task and Next[fte, te] and %s[te, fte])", getActivation(one, fFnName), one.taskB(), sFnName)
        		)
        );
        alloy.append( fnGen.generateFunction(fFnName, one.getFirstFunction(), map, one.getArgs()) );
        alloy.append( fnGen.generateFunction(sFnName, one.getSecondFunction(), map, one.getArgs()) );
    }
    
    private void addSuccessionDataConstraint(DataConstraint one, String fFnName, String sFnName) throws DeclareParserException, GenerationException {
    	// Succession[A, B] = Response[A, B] and Precedence[A, B]
    	String resp = String.format("%s(some fte: Event | %s = fte.task and %s[te, fte] and After[te, fte])", getActivation(one, fFnName), one.taskB(), sFnName);
    	String prec = String.format("all te: Event | %s = te.task implies (some fte: Event | %s = fte.task and %s[fte, te] and %s[fte] and After[fte, te])", one.taskB(), one.taskA(), sFnName, fFnName);
    	String succ = "(" + resp + ") and (" + prec + ")";
    	
    	alloyConstraints.add( Pair.of(one.getStatement(), succ) );
        alloy.append( fnGen.generateFunction(fFnName, one.getFirstFunction(), map, one.getArgs()) );
        alloy.append( fnGen.generateFunction(sFnName, one.getSecondFunction(), map, one.getArgs()) );
    }
    
    private void addAlternateSuccessionDataConstraint(DataConstraint one, String fFnName, String sFnName) throws DeclareParserException, GenerationException {
    	// AlternateSuccession[A, B] = AlternateResponse[A, B] and AlternatePrecedence[A, B]
    	String altResp = String.format("%s(some fte: Event | %s = fte.task and %s[te, fte] and After[te, fte] and (no ite: Event | %s = ite.task and %s[ite] and After[te, ite] and After[ite, fte]))", getActivation(one, fFnName), one.taskB(), sFnName, one.taskA(), fFnName);
    	String altPrec = String.format("all te: Event | %s = te.task implies (some fte: Event | %s = fte.task and %s[fte, te] and %s[fte] and After[fte, te] and (no ite: Event | %s = ite.task and %s[fte, ite] and After[fte, ite] and After[ite, te]))", one.taskB(), one.taskA(), sFnName, fFnName, one.taskB(), sFnName);
    	String altSucc = "(" + altResp + ") and (" + altPrec + ")";
    	
    	alloyConstraints.add( Pair.of(one.getStatement(), altSucc) );
        alloy.append( fnGen.generateFunction(fFnName, one.getFirstFunction(), map, one.getArgs()) );
        alloy.append( fnGen.generateFunction(sFnName, one.getSecondFunction(), map, one.getArgs()) );
    }

	private void addChainSuccessionDataConstraint(DataConstraint one, String fFnName, String sFnName) throws DeclareParserException, GenerationException {
		// ChainSuccession[A, B] = ChainResponse[A, B] and ChainPrecedence[A, B]
		String chainResp = String.format("%s(some fte: Event | %s = fte.task and Next[te, fte] and %s[te, fte])", getActivation(one, fFnName), one.taskB(), sFnName);
    	String chainPrec = String.format("all te: Event | %s = te.task implies (some fte: Event | %s = fte.task and Next[fte, te] and %s[fte, te] and %s[fte])", one.taskB(), one.taskA(), sFnName, fFnName);
    	String chainSucc = "(" + chainResp + ") and (" + chainPrec + ")";
    	
    	alloyConstraints.add( Pair.of(one.getStatement(), chainSucc) );
        alloy.append( fnGen.generateFunction(fFnName, one.getFirstFunction(), map, one.getArgs()) );
        alloy.append( fnGen.generateFunction(sFnName, one.getSecondFunction(), map, one.getArgs()) );
	}

    private void addNotRespondedExistenceDataConstraint(DataConstraint one, String fFnName, String sFnName) throws DeclareParserException, GenerationException {
        alloyConstraints.add(
        		Pair.of(one.getStatement(),
        				String.format("%s(no ote: Event | %s = ote.task and %s[te, ote])", getActivation(one, fFnName), one.taskB(), sFnName)
        		)
        );
        alloy.append( fnGen.generateFunction(fFnName, one.getFirstFunction(), map, one.getArgs()) );
        alloy.append( fnGen.generateNotFunction(sFnName, one.getSecondFunction(), map, one.getArgs()) );
    }

    private void addNotResponseDataConstraint(DataConstraint one, String fFnName, String sFnName) throws DeclareParserException, GenerationException {
        alloyConstraints.add(
        		Pair.of(one.getStatement(),
        				String.format("%s(no fte: Event | %s = fte.task and %s[te, fte] and After[te, fte])", getActivation(one, fFnName), one.taskB(), sFnName)
        		)
        );
        alloy.append( fnGen.generateFunction(fFnName, one.getFirstFunction(), map, one.getArgs()) );
        alloy.append( fnGen.generateNotFunction(sFnName, one.getSecondFunction(), map, one.getArgs()) );
    }

    private void addNotChainResponseDataConstraint(DataConstraint one, String fFnName, String sFnName) throws DeclareParserException, GenerationException {
        String a = getActivation(one, fFnName);
        alloyConstraints.add(
        		Pair.of(one.getStatement(),
        				String.format("(%s(no fte: Event | %s = fte.task and Next[te, fte] and %s[te, fte])) and "
        								+ "(%s(no fte: Event | DummyActivity = fte.task and Next[te, fte]))", a, one.taskB(), sFnName, a)
        		)
        );
        alloy.append( fnGen.generateFunction(fFnName, one.getFirstFunction(), map, one.getArgs()) );
        alloy.append( fnGen.generateNotFunction(sFnName, one.getSecondFunction(), map, one.getArgs()) );
    }

    private void addNotPrecedenceDataConstraint(DataConstraint one, String fFnName, String sFnName) throws DeclareParserException, GenerationException {
        alloyConstraints.add(
        		Pair.of(one.getStatement(),
        				String.format("%s(no fte: Event | %s = fte.task and %s[te, fte] and After[fte, te])", getActivation(one, fFnName), one.taskB(), sFnName)
        		)
        );
        alloy.append( fnGen.generateFunction(fFnName, one.getFirstFunction(), map, one.getArgs()) );
        alloy.append( fnGen.generateNotFunction(sFnName, one.getSecondFunction(), map, one.getArgs()) );
    }

    private void addNotChainPrecedenceDataConstraint(DataConstraint one, String fFnName, String sFnName) throws DeclareParserException, GenerationException {
        String a = getActivation(one, fFnName);
        alloyConstraints.add(
        		Pair.of(one.getStatement(),
        				String.format("(%s(no fte: Event | %s = fte.task and Next[fte, te] and %s[te, fte])) and "
        								+ "(%s(no fte: Event | DummyActivity = fte.task and Next[fte, te]))", a, one.taskB(), sFnName, a)
        		)
        );
        alloy.append( fnGen.generateFunction(fFnName, one.getFirstFunction(), map, one.getArgs()) );
        alloy.append( fnGen.generateNotFunction(sFnName, one.getSecondFunction(), map, one.getArgs()) );
    }

    private void addChoiceDataConstraint(DataConstraint one, String fFnName, String sFnName) throws DeclareParserException, GenerationException {
        alloyConstraints.add(
        		Pair.of(one.getStatement(),
        				String.format("some te: Event | te.task = %s and %s[te] or te.task = %s and %s[te, te]", one.taskA(), fFnName, one.taskB(), sFnName)
        		)
        );
        alloy.append( fnGen.generateFunction(fFnName, one.getFirstFunction(), map, one.getArgs()) );
        alloy.append( fnGen.generateFunction(sFnName, one.getSecondFunction(), map, one.getArgs()) );
    }

    private void addExclusiveChoiceDataConstraint(DataConstraint one, String fFnName, String sFnName) throws DeclareParserException, GenerationException {
        alloyConstraints.add(
        		Pair.of(one.getStatement(),
        				String.format("(some te: Event | te.task = %s and %s[te] or te.task = %s and %s[te, te]) and "
        								+ "((no te: Event | %s = te.task and %s[te]) or (no te: Event | %s = te.task and %s[te, te]))", one.taskA(), fFnName, one.taskB(), sFnName, one.taskA(), fFnName, one.taskB(), sFnName)
        		)
        );
        alloy.append( fnGen.generateFunction(fFnName, one.getFirstFunction(), map, one.getArgs()) );
        alloy.append( fnGen.generateFunction(sFnName, one.getSecondFunction(), map, one.getArgs()) );
    }

    private String getActivation(DataConstraint one, String fFnName) {
        return String.format("all te: Event | (%s = te.task and %s[te]) implies ", one.taskA(), fFnName);
    }
}
