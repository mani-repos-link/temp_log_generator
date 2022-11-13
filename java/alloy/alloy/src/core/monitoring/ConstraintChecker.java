package core.monitoring;

import edu.mit.csail.sdg.alloy4.Err;
import edu.mit.csail.sdg.alloy4compiler.ast.Module;
import edu.mit.csail.sdg.alloy4compiler.translator.A4Solution;
import declare.DeclareParserException;
import core.exceptions.GenerationException;
import core.alloy.codegen.AlloyCodeGenerator;
import core.alloy.codegen.NameEncoder;
import core.alloy.integration.AlloyComponent;
import declare.lang.Constraint;
import declare.lang.DataConstraint;
import declare.lang.Statement;
import declare.DeclareModel;
import declare.DeclareParser;

import org.deckfour.xes.extension.std.XConceptExtension;
import org.deckfour.xes.extension.std.XExtendedEvent;
import org.deckfour.xes.model.XTrace;
import org.deckfour.xes.model.impl.XAttributeMapImpl;
import org.deckfour.xes.model.impl.XTraceImpl;

import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public class ConstraintChecker {
    private List<Constraint> constraints;
    private List<DataConstraint> dataConstraints;
    private DeclareModel model;
    private NameEncoder encodings;
    private XTrace trace;
    private int minTraceLen;
    private int maxTraceLen;

    // TODO: Should maybe add bitwidth and filename to the constructor
    private int bitwidth = 5;
    private String alsFilename = "monitoring_temp.als";
    
    private String [][] declaredMatrix;
    private String [] constraintNames;
    private String [] dataConstraintsName;
    private List<List<DataConstraint>> conflictedConstraints = new ArrayList<>();
    private DataConstraint[] allConstraintArr;
    private List<Constraint> permViolatedCon = new ArrayList<>();
    private List<Constraint> permViolatedDataCon = new ArrayList<>();

    public ConstraintChecker(DeclareModel model) {
    	this.trace = new XTraceImpl(new XAttributeMapImpl());
    	
    	this.model = model;
        this.constraints = model.getConstraints();
        this.dataConstraints = model.getDataConstraints();
        this.allConstraintArr = Stream.concat(
    			constraints.stream().map(c -> new DataConstraint(c.getName(), c.getArgs(), null, c.getStatement())), 
    			dataConstraints.stream()
    		).toArray(DataConstraint[]::new);
        
        this.dataConstraintsName = model.getDataConstraints().stream()
							    		.map(DataConstraint::getStatement)
							    		.map(Statement::getCode)
							    		.toArray(String[]::new);
        
        constraintNames = setConstraintStringNames(constraints, dataConstraintsName);
    }
    
    public void setTrace(XTrace trace) {
        this.trace = trace;
    }
    
    private String [] setConstraintStringNames(List<Constraint> constraints, String[] dataConstraintsName) {
    	String [] constraintNames = new String[constraints.size() + dataConstraintsName.length];
        
        int i = 0;
        
        for (Constraint c : constraints) {
            if (!c.isBinary())
                constraintNames[i] = c.getName() + "([" + encodings.getActivityMapping().get(c.taskA()) + "])";
            else
            	constraintNames[i] = c.getName() + "(["
										+ encodings.getActivityMapping().get(c.taskA()) + ", "
										+ encodings.getActivityMapping().get(c.taskB()) + "])";
            i++;
        }
        
        for (String s : dataConstraintsName) {
        	String inBrackets = s.substring(s.indexOf("[")+1, s.indexOf("]")); // inside [ ]
            List<String> acts = Arrays.asList( inBrackets.split(", ") );
            
            if (acts.size() == 4 || acts.size() == 3) {
                String sb = "";
                
                if (acts.size()==3 && acts.get(1).length()>acts.get(2).length())
                    sb = acts.get(0) + ", " + acts.get(1);
                
                else
                    sb = acts.get(0) + ", " + acts.get(2);
                
                s = s.replace(inBrackets, sb);
            }
            
            int ind = s.indexOf("[");
            StringBuffer newString = new StringBuffer(s);
            newString.insert(ind, "(");
            newString.append(")");
            constraintNames[i] = newString.toString();
            
            i++;
        }
        
        return constraintNames;
    }

    public void setFinal() {
        for (Constraint c : constraints) {
            if (c.getState() == Constraint.State.POSSIBLY_SATISFIED)
                c.setState(Constraint.State.PERMANENTLY_SATISFIED);
            else if (c.getState() == Constraint.State.POSSIBLY_VIOLATED)
                c.setState(Constraint.State.PERMANENTLY_VIOLATED);
        }
        
        for (DataConstraint dc: dataConstraints) {
            if (dc.getState() == Constraint.State.POSSIBLY_SATISFIED)
                dc.setState(Constraint.State.PERMANENTLY_SATISFIED);
            else if (dc.getState() == Constraint.State.POSSIBLY_VIOLATED)
                dc.setState(Constraint.State.PERMANENTLY_VIOLATED);
        }
    }

    public void run() throws DeclareParserException, Err, GenerationException {
        
        if (constraints != null && !constraints.isEmpty())
            for (Constraint c : constraints)
            	c.setState(checkCurrentState(c));

        if (dataConstraints != null && !dataConstraints.isEmpty())
            for (DataConstraint dc : dataConstraints)
                dc.setState(checkCurrentState(dc));
    }
    
    private Constraint.State checkCurrentState(Constraint c) throws DeclareParserException, GenerationException, Err {
    	setInitialSize();
    	
    	Constraint.State state = null;
    	
    	switch(c.getName()) {
    	case "Init":
    		state = initChecker(c);
    		break;
        case "End":
        	state = endChecker(c);
        	break;
        case "Absence":
        	state = absenceChecker(c);
        	break;
        case "Existence":
        	state = existenceChecker(c);
            break;
        case "Exactly":
        	state = exactlyChecker(c);
        	break;
        case "RespondedExistence":
        	state = respondedExistenceChecker(c);
        	break;
        case "CoExistence":
        	state = coExistenceChecker(c);
        	break;
        case "Response":
        	state = responseChecker(c);
        	break;
        case "AlternateResponse":
        case "ChainResponse":
        	state = chainResponseChecker(c);
        	break;
        case "Precedence":
        	state = precedenceChecker(c);
        	break;
        case "AlternatePrecedence":
        case "ChainPrecedence":
        	state = chainPrecedenceChecker(c);
        	break;
        case "Succession":
        case "AlternateSuccession":
        case "ChainSuccession":
        	state = successionChecker(c);
        	break;
        case "NotRespondedExistence":
        case "NotResponse":
        case "NotPrecedence":
        case "NotChainResponse":
        case "NotChainPrecedence":
        	state = notRespondedExistenceChecker(c);
        	break;
        case "Choice":
        	state = choiceChecker(c);
        	break;
        case "ExclusiveChoice":
        	state = exclusiveChoiceChecker(c);
        	break;
        
        default:
        	throw new DeclareParserException("Not supported template: " + c.getName());
    	}
    	
    	
    	//traceAndStatePrintOut(c);
    	
    	if (state == Constraint.State.PERMANENTLY_VIOLATED) {
    		if (c instanceof DataConstraint)
                permViolatedDataCon.add(c);
            else
                permViolatedCon.add(c);
    	}
    	
    	return state;
    }
    
    public NameEncoder getEncodings() {
		return encodings;
	}

	public void setEncodings(NameEncoder encodings) {
		this.encodings = encodings;
	}

	// We set the initial Trace size:
    public void setInitialSize() {
        minTraceLen = trace.size();
        maxTraceLen = trace.size();
    }

    private Constraint.State absenceChecker(Constraint c) throws DeclareParserException, Err, GenerationException {
    	Constraint.State state = null;
    	
    	if (!checkConstraint(c))	// If false then violated because that it is absence and something is present then this is bad.
            state = Constraint.State.PERMANENTLY_VIOLATED;	// should be this
        else	// Possibly satisfied because we don't know whether there will be possibly this thing occurring in the future.
        	state = Constraint.State.POSSIBLY_SATISFIED;
        
        return state;
    }

    private Constraint.State exactlyChecker(Constraint c) throws DeclareParserException, Err, GenerationException {
    	Constraint.State state = null;
    	
    	if (checkConstraint(c)) {
    		state = Constraint.State.POSSIBLY_SATISFIED;
            
        } else {	// if value wrong
        	maxTraceLen += Integer.parseInt(c.taskB());
            //System.out.println("Checking same constraint with prefix size + N: ");

            if (checkConstraint(c))
            	state = Constraint.State.POSSIBLY_VIOLATED;	// right now violated but a possibility get it right in the future. A has occured less then N times
            else
            	state = Constraint.State.PERMANENTLY_VIOLATED;	// Activity A has occurred more than N times which means that the trace is broken beyond repair.
        }
        
    	return state;
    }
    
    private Constraint.State existenceChecker(Constraint c) throws DeclareParserException, Err, GenerationException {
    	Constraint.State state = null;
    	
    	if (checkConstraint(c)) {	// Because when we get the value true here then it means it exists and it will be always satisfied from now on.
    		state = Constraint.State.PERMANENTLY_SATISFIED;
        
        } else {	// if value is false then err we check for maxTracesize + len.
            maxTraceLen += Integer.parseInt(c.taskB());	// yeap this is correct.
            state = Constraint.State.POSSIBLY_VIOLATED;
            //System.out.println("Checking same constraint with maxTraceLen + N: ");

            if (!checkConstraint(c))	// if we don't find a solution then it is permanently violated if it is okay, then it will still be possibly violated.
            	state = Constraint.State.PERMANENTLY_VIOLATED;
        }
        
    	return state;
    }
    
    private Constraint.State initChecker(Constraint c) throws DeclareParserException, Err, GenerationException {
    	Constraint.State state = null;
    	
    	if (checkConstraint(c))	// if true, then all fine and the event will always be the first event
    		state = Constraint.State.PERMANENTLY_SATISFIED;
        else
        	state = Constraint.State.PERMANENTLY_VIOLATED;
        
        return state;
    }
    
    private Constraint.State endChecker(Constraint c) throws DeclareParserException, Err, GenerationException {
    	Constraint.State state = null;
    	
    	if (checkConstraint(c))
    		state = Constraint.State.POSSIBLY_SATISFIED;
    	else
    		state = Constraint.State.POSSIBLY_VIOLATED;
    	
    	return state;
    }

    private Constraint.State choiceChecker(Constraint c) throws DeclareParserException, Err, GenerationException {
    	Constraint.State state = null;
    	
    	if (checkConstraint(c))
    		state = Constraint.State.PERMANENTLY_SATISFIED;
        else
        	state = Constraint.State.POSSIBLY_VIOLATED;
        
        return state;
    }
    
    private Constraint.State exclusiveChoiceChecker(Constraint c) throws DeclareParserException, Err, GenerationException {
    	Constraint.State state = null;
    	
    	if (checkConstraint(c)) {
    		state = Constraint.State.POSSIBLY_SATISFIED;
        
    	} else {
            maxTraceLen++;
            if (checkConstraint(c))
            	state = Constraint.State.POSSIBLY_VIOLATED;
            else
            	state = Constraint.State.PERMANENTLY_VIOLATED;
        }
    	
        return state;
    }

    private Constraint.State respondedExistenceChecker(Constraint c) throws DeclareParserException, Err, GenerationException {
    	Constraint.State state = null;
    	
    	String actCondition = c.getStatement().getCode().split("\\|")[1];
    	Statement actExistenceSt = new Statement("Existence["+c.taskA()+", 1] |"+actCondition+" |", 0);
    	DataConstraint actExistence = DeclareParser.parseDataConstraints( List.of(actExistenceSt) ).get(0);
    	
    	if (checkCurrentState(actExistence) == Constraint.State.PERMANENTLY_SATISFIED) {
    		
    		String trgCondition = c.getStatement().getCode().split("\\|")[2];
    		Statement trgExistenceSt = new Statement("Existence["+c.taskB()+", 1] |"+trgCondition+" |", 0);
    		DataConstraint trgExistence = DeclareParser.parseDataConstraints( List.of(trgExistenceSt) ).get(0);
    		
    		if (checkCurrentState(trgExistence) == Constraint.State.PERMANENTLY_SATISFIED)
    			state = Constraint.State.PERMANENTLY_SATISFIED;
    		else
    			state = Constraint.State.POSSIBLY_VIOLATED;
    		
    	} else {	// Vacuous satisfaction
    		state = Constraint.State.POSSIBLY_SATISFIED;
    	}
    	
    	return state;
    }
    
    private Constraint.State coExistenceChecker(Constraint c) throws DeclareParserException, Err, GenerationException {
    	Constraint.State state = null;
    	
    	String actCondition = c.getStatement().getCode().split("\\|")[1];
    	String trgCondition = c.getStatement().getCode().split("\\|")[2];
    	
    	Statement respEx1 = new Statement("RespondedExistence["+c.taskA()+", "+c.taskB()+"] |"+actCondition+" |"+trgCondition+" |", 0);
    	Statement respEx2 = new Statement("RespondedExistence["+c.taskB()+", "+c.taskA()+"] |"+actCondition+" |"+trgCondition+" |", 0);
    	
    	List<DataConstraint> list = DeclareParser.parseDataConstraints( List.of(respEx1, respEx2) );
    	List<Constraint.State> outcomes = new ArrayList<>();
    	
    	for (DataConstraint dc : list)
    		outcomes.add(checkCurrentState(dc));
    	
    	if (outcomes.contains(Constraint.State.PERMANENTLY_VIOLATED))
    		state = Constraint.State.PERMANENTLY_VIOLATED;
    	else if (outcomes.contains(Constraint.State.POSSIBLY_VIOLATED))
    		state = Constraint.State.POSSIBLY_VIOLATED;
    	else if (outcomes.contains(Constraint.State.POSSIBLY_SATISFIED))
    		state = Constraint.State.POSSIBLY_SATISFIED;
    	else
    		state = Constraint.State.PERMANENTLY_SATISFIED;
    	
    	return state;
    }

    private Constraint.State responseChecker(Constraint c) throws DeclareParserException, Err, GenerationException {
    	Constraint.State state = null;
    	
    	if (checkConstraint(c)) {
    		state = Constraint.State.POSSIBLY_SATISFIED;
        
        } else {
        	state = Constraint.State.POSSIBLY_VIOLATED;
            
            // TODO: the numberOfActivations computing is wrong because it should take into account of the activation condition!
            int numberOfActivations = (int) trace.stream().filter(evt -> XConceptExtension.instance().extractName(evt).equals(c.taskA())).count();
            maxTraceLen = maxTraceLen + numberOfActivations;
            //System.out.println("Checking same constraint with a larger upper bound now: ");

            if (!checkConstraint(c))
            	state = Constraint.State.POSSIBLY_VIOLATED; // Because it cannot find a solution even with a larger upperbound.
            else
            	state = Constraint.State.POSSIBLY_VIOLATED; // Although we don't really need these lines do we
        }
        
    	return state;
    }
    
    private Constraint.State chainResponseChecker(Constraint c) throws DeclareParserException, Err, GenerationException {
    	Constraint.State state = null;
    	
    	if (checkConstraint(c)) {
    		state = Constraint.State.POSSIBLY_SATISFIED; // might get rekted
            
        } else {
        	//System.out.println("Checking same constraint for prefix size + 1");
            maxTraceLen++;
            
            if (!checkConstraint(c))
            	state = Constraint.State.PERMANENTLY_VIOLATED; // if does not find the right solution with prefix + 1
            else
            	state = Constraint.State.POSSIBLY_VIOLATED;
        }
        
    	return state;
    }
    
    private Constraint.State precedenceChecker(Constraint c) throws DeclareParserException, Err, GenerationException {
    	Constraint.State state = null;
    	
    	if (checkConstraint(c)) {
    		String actCondition = c.getStatement().getCode().split("\\|")[1];
        	Statement actExistenceSt = new Statement("Existence["+c.taskA()+", 1] |"+actCondition+" |", 0);
        	DataConstraint actExistence = DeclareParser.parseDataConstraints( List.of(actExistenceSt) ).get(0);
        	
    		if (checkCurrentState(actExistence) == Constraint.State.PERMANENTLY_SATISFIED)
    			state = Constraint.State.PERMANENTLY_SATISFIED;
    		else	// Vacuous satisfaction
    			state = Constraint.State.POSSIBLY_SATISFIED;
    		
    	} else {
        	state = Constraint.State.PERMANENTLY_VIOLATED;
    	}
    	
    	return state;
    }
    
    private Constraint.State chainPrecedenceChecker(Constraint c) throws DeclareParserException, Err, GenerationException {
    	Constraint.State state = null;
    	    	
    	if (checkConstraint(c))
    		state = Constraint.State.POSSIBLY_SATISFIED;
        else
        	state = Constraint.State.PERMANENTLY_VIOLATED;
        
    	return state;
    }
    
    private Constraint.State successionChecker(Constraint c) throws DeclareParserException, Err, GenerationException {
    	Constraint.State state = null;
    	
    	String precTemplate = null, respTemplate = null;
    	switch (c.getName()) {
    	case "Succession":
    		precTemplate = "Precedence";
    		respTemplate = "Response";
    		break;
    	case "AlternateSuccession":
    		precTemplate = "AlternatePrecedence";
    		respTemplate = "AlternateResponse";
    		break;
    	case "ChainSuccession":
    		precTemplate = "ChainPrecedence";
    		respTemplate = "ChainResponse";
    		break;
    	}
    	
    	String actCondition = c.getStatement().getCode().split("\\|")[1];
    	String trgCondition = c.getStatement().getCode().split("\\|")[2];
    	
    	Statement respSt = new Statement(respTemplate+"["+c.taskA()+", "+c.taskB()+"] |"+actCondition+" |"+trgCondition+" |", 0);
    	Statement precSt = new Statement(precTemplate+"["+c.taskB()+", "+c.taskA()+"] |"+actCondition+" |"+trgCondition+" |", 0);
    	
    	List<DataConstraint> list = DeclareParser.parseDataConstraints( List.of(respSt, precSt) );
    	List<Constraint.State> outcomes = new ArrayList<>();
    	
    	for (DataConstraint dc : list)
    		outcomes.add(checkCurrentState(dc));
    	
    	if (outcomes.contains(Constraint.State.PERMANENTLY_VIOLATED))
    		state = Constraint.State.PERMANENTLY_VIOLATED;
    	else if (outcomes.contains(Constraint.State.POSSIBLY_VIOLATED))
    		state = Constraint.State.POSSIBLY_VIOLATED;
    	else if (outcomes.contains(Constraint.State.POSSIBLY_SATISFIED))
    		state = Constraint.State.POSSIBLY_SATISFIED;
    	else
    		state = Constraint.State.PERMANENTLY_SATISFIED;
    	
    	return state;
    }
    
    private Constraint.State notRespondedExistenceChecker(Constraint c) throws DeclareParserException, Err, GenerationException {
    	Constraint.State state = null;
    	
    	if (checkConstraint(c))
    		state = Constraint.State.POSSIBLY_SATISFIED;
        else
    		state = Constraint.State.PERMANENTLY_VIOLATED;
    	
    	return state;
    }

    // Printing functions
    public void traceAndStatePrintOut(Constraint c) {
    	String out = "The trace is ";
        
        switch (c.getState()) {
        case PERMANENTLY_SATISFIED:
        	out += "permanently satisfied";
            break;
        case PERMANENTLY_VIOLATED:
        	out += "permanently violated";
            break;
        case POSSIBLY_VIOLATED:
        	out += "possibly violated";
            break;
        case POSSIBLY_SATISFIED:
        	out += "possibly satisfied";
            break;
        default:
        	out += "error!";
            break;
        }
        
        out += " for the constraint " + checkedConstraintPrintOut(c);
        
        System.out.println(out);
    }
    
    private String checkedConstraintPrintOut(Constraint c) {
    	String out = c.getName();
    	
    	switch (c.getName()) {
		case "Existence":
		case "Absence":
		case "Exactly":
			if (c.isBinary())
				out += encodings.getActivityMapping().get(c.taskB())
					+ "[" + encodings.getActivityMapping().get(c.taskA()) + "]";
			else
				out += "1[" + encodings.getActivityMapping().get(c.taskA()) + "]";
			break;
		
		case "Init":
			out += "[" + encodings.getActivityMapping().get(c.taskA()) + "]";
			break;
			
		case "Choice":
		case "ExclusiveChoice":
		case "RespondedExistence":
		case "Precedence":
		case "AlternatePrecedence":
		case "ChainPrecedence":
		case "Response":
		case "AlternateResponse":
		case "ChainResponse":
		case "Succession":
		case "AlternateSuccession":
		case "ChainSuccession":
		case "NotRespondedExistence":
		case "NotPrecedence":
		case "NotChainPrecedence":
		case "NotResponse":
		case "NotChainResponse":
			out += "[" + encodings.getActivityMapping().get(c.taskA()) + ", " + encodings.getActivityMapping().get(c.taskB()) + "]";
			break;
		}
    	
    	return out;
    }
    
    // Checking constraints and stuff methods
    private boolean checkConstraint(Constraint c) throws DeclareParserException, Err, GenerationException {
    	boolean isDataConstraint = c instanceof DataConstraint;
        
    	DeclareModel dummyModel = new DeclareModel(model);
    	if (isDataConstraint) {
    		dummyModel.setConstraints(Collections.emptyList());
    		dummyModel.setDataConstraints( List.of((DataConstraint) c) );
    	} else {
    		dummyModel.setConstraints( List.of(c) );
    		dummyModel.setDataConstraints(Collections.emptyList());
    	}
    	
    	AlloyCodeGenerator gen = new AlloyCodeGenerator(maxTraceLen, minTraceLen, bitwidth, 1, false, false, true);
        gen.runConstraintChecker(dummyModel, c, false, isDataConstraint);
        String alloyCode = gen.getAlloyCode();
        
        TraceAlloyCode traceGen = new TraceAlloyCode();
        traceGen.setNumericData(gen.getNumericData());
        traceGen.run(trace, dummyModel, isDataConstraint);
        String traceCode = traceGen.getTraceCode();
        
        return alloyCheck(alloyCode + traceCode);
    }
    
    public boolean checkFullConjuction() throws GenerationException, DeclareParserException, Err {
        AlloyCodeGenerator gen = new AlloyCodeGenerator((maxTraceLen + 2), minTraceLen, bitwidth, 1, false, false, true);
        gen.runLogGeneration(model, false, 1, null, "monitoring");
        String alloyCode = gen.getAlloyCode();
        
        TraceAlloyCode traceGen = new TraceAlloyCode();
        traceGen.setNumericData(gen.getNumericData());
        traceGen.run(trace, model, true);
        String traceCode = traceGen.getTraceCode(); // This always same
        
        return alloyCheck(alloyCode+traceCode);
    }

    public boolean checkSublistConjunction(int n, int r) throws GenerationException, DeclareParserException, Err {
        // We do SubLists
        ArrayList<ArrayList<DataConstraint>> subLists = new ArrayList<>();
        subLists = printCombination(allConstraintArr,n, r,subLists); // See on selle t�ieliku subsetiga onja.
        
        boolean overAllSolution = true;
        
        for (ArrayList<DataConstraint> subList : subLists) {
            AlloyCodeGenerator gen = new AlloyCodeGenerator((maxTraceLen + 6), minTraceLen, bitwidth, 1, false, false, true);
            TraceAlloyCode traceGen = new TraceAlloyCode();
            
            boolean solution = checkSubSet(subList, traceGen, gen); // Me saame, siin kas true v�i false, kui on false, siis njoormilt saame asju teha n��d.
            if (!solution) { // see on n��d false aga mis v�rk on see ,et meil on siis ainult �ks subList checkitud
                overAllSolution = false;
                
                int i = 0;
                while (i < subList.size()) {
                    DataConstraint dc = subList.get(i);
                    
                    if (dc.getFunctions() == null){ // We know this is a new fake dataConstraint that is actually a Constraint so we have to find a constraint that corresponds to the original one.
                        for (Constraint c : constraints) {
                            // With this if clause we try to find whether to constraints are exactly the same
                            if (c.getName().equals(dc.getName()) && c.getArgs().equals(dc.getArgs()) && c.getStatement().getCode().equals(dc.getStatement().getCode()) && c.getStatement().getLine() == dc.getStatement().getLine()) {
                                c.setState(Constraint.State.STATE_CONFLICT);
                                System.out.println("Conflictis on c: " + c.getName());
                            }
                        }
                    
                    } else {
                        dc.setState(Constraint.State.STATE_CONFLICT);
                        System.out.println("Conflictis on dc: " + dc.getName());
                    }
                    
                    i++;
                }
            }
        }
        
        return overAllSolution;
    }
    
    boolean checkSubSet(ArrayList<DataConstraint> subList, TraceAlloyCode traceGen, AlloyCodeGenerator gen) throws Err, GenerationException, DeclareParserException {
        boolean isPermViolated = false;
        boolean isSubList = false;

        for (DataConstraint dc: subList) {
            if (dc.getFunctions() == null)
                for(Constraint c : constraints)
                    if (c.getName().equals(dc.getName()) && c.getArgs().equals(dc.getArgs())
                    		&& c.getStatement().getCode().equals(dc.getStatement().getCode())
                    		&& c.getStatement().getLine() == dc.getStatement().getLine())
                        if (c.getState() == Constraint.State.PERMANENTLY_VIOLATED)
                            isPermViolated = true;
                        
            if (dc.getState() == Constraint.State.PERMANENTLY_VIOLATED)
                isPermViolated = true;
        }
        
        for (List<DataConstraint> conflictedConstraint : conflictedConstraints)
            if (subList.containsAll(conflictedConstraint))
                isSubList = true;
            
        if (!conflictedConstraints.contains(subList) && !isPermViolated && !isSubList) {
            gen.runConflictChecker(model, subList, false);
            traceGen.setNumericData(gen.getNumericData());
            traceGen.run(trace, model, true);
            String alloyCode = gen.getAlloyCode();
            String traceCode = traceGen.getTraceCode();
            String allAlloyCode = alloyCode +traceCode;
            boolean solution = (alloyCheck(allAlloyCode));
            //System.out.println("in isDConstraint");
            
            if (!solution)
                conflictedConstraints.add(subList);
            
            return solution;
        
        } else {
            return true;
        }
    }

    public boolean checkModel(int min, int max) throws GenerationException, DeclareParserException, Err {
        minTraceLen = min;
        maxTraceLen = max;
        AlloyCodeGenerator gen = new AlloyCodeGenerator(maxTraceLen, minTraceLen, bitwidth, 1, false, false, true);
        gen.runLogGeneration(model, false, 1, null, "monitoring");
        String alloyCode = gen.getAlloyCode();
        //System.out.println("in checkModel");
        
        return alloyCheck(alloyCode);
    }

    private boolean alloyCheck(String allAlloyCode) throws Err {
        writeAllText(alsFilename, allAlloyCode);
        AlloyComponent alloy = new AlloyComponent();
        Module world = alloy.parse(alsFilename);
        A4Solution solution = alloy.executeFromFile(maxTraceLen, bitwidth);
        //Global.log.accept("Found Solution: " + (solution != null && solution.satisfiable()));
        
        return solution != null && solution.satisfiable();
    }

    private static void writeAllText(String filename, String text) {
        try(  PrintWriter out = new PrintWriter( new FileWriter(filename, false) )  ){
            out.print( text );
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    //This is for when the trace is new and starts with StartDummy so we set everything to possibly satisfied;
    public String [][] initMatrix(){
        int sizeOfConstraints = constraints.size() + dataConstraints.size();
        String[][]newMatrix = new String[sizeOfConstraints][1];
        declaredMatrix = newMatrix;// Here we set that we can keep tabs on the old matrix.
        return newMatrix;
    }

    private String [][] getRealMatrix(){// We need to add another column to the old matrix so we are creating a completely new one.
        int sizeOfConstraints =constraints.size() + dataConstraints.size();
        int sizeOfTrace = (trace.size());
        String [][] realMatrix = new String[sizeOfConstraints][sizeOfTrace]; // But the size in place.
        for(int i = 0; i < declaredMatrix.length; i++){
            for (int j = 0; j<declaredMatrix[0].length; j++){
                realMatrix[i][j] = declaredMatrix[i][j];
            }
        }
        return realMatrix;
    }

    private ArrayList<ArrayList<DataConstraint>> getCombination(DataConstraint[] arr, int n, int r,
                                                  int index, DataConstraint[] data, int i, ArrayList<ArrayList<DataConstraint>> returnList)
    {
        // Current combination is ready to be printed,
        // print it
        if (index == r) {
            ArrayList<DataConstraint> arrL = new ArrayList<>();
            for (int j = 0; j < r; j++) {
                arrL.add(data[j]);
            }
            returnList.add(arrL);

            return returnList;
        }

        // When no more elements are there to put in data[]
        if (i >= n)
            return returnList;

        // current is included, put next at next
        // location
        data[index] = arr[i];
        getCombination(arr, n, r, index + 1,
                data, i + 1, returnList);

        // current is excluded, replace it with
        // next (Note that i+1 is passed, but
        // index is not changed)
        getCombination(arr, n, r, index, data, i + 1, returnList);
        return returnList;
    }

    // The main function that prints all combinations
    // of size r in arr[] of size n. This function
    // mainly uses combinationUtil()
    private ArrayList<ArrayList<DataConstraint>> printCombination(DataConstraint[] arr, int n, int r, ArrayList<ArrayList<DataConstraint>> dcList)
    {
        // A temporary array to store all combination
        // one by one
        DataConstraint data[] = new DataConstraint[r];

        // Print all combination using temprary
        // array 'data[]'
        dcList = getCombination(arr, n, r, 0, data, 0, dcList);
        return dcList;
    }

    private String[][] updateNewMatrix() {// we have done the checking before so the constraint state  should be there already.
        String [][] realMatrix = getRealMatrix();
        int i = 0;
        int lastMatrixColumnIndex = ((realMatrix[0].length) -1); // J�lle dummystart
        for (Constraint c : constraints) {
            switch (c.getState()) {
                case STATE_CONFLICT:
                    realMatrix[i][lastMatrixColumnIndex] = "conflict";
                    break;
                case PERMANENTLY_SATISFIED:
                    realMatrix[i][lastMatrixColumnIndex] = "sat";
                    break;
                case PERMANENTLY_VIOLATED:
                    realMatrix[i][lastMatrixColumnIndex] = "viol";
                    break;
                case POSSIBLY_VIOLATED:
                    realMatrix[i][lastMatrixColumnIndex] = "poss.viol";
                    break;
                case POSSIBLY_SATISFIED:
                    realMatrix[i][lastMatrixColumnIndex] = "poss.sat";
                    break;
                default:
                    realMatrix[i][lastMatrixColumnIndex] = "unknown";
                    break;
            }
            i++;
        }
        for (DataConstraint dc : dataConstraints){
            switch (dc.getState()) {
                case STATE_CONFLICT:
                    realMatrix[i][lastMatrixColumnIndex] = "conflict";
                    break;
                case PERMANENTLY_SATISFIED:
                    realMatrix[i][lastMatrixColumnIndex] = "sat";
                    break;
                case PERMANENTLY_VIOLATED:
                    realMatrix[i][lastMatrixColumnIndex] = "viol";
                    break;
                case POSSIBLY_VIOLATED:
                    realMatrix[i][lastMatrixColumnIndex] = "poss.viol";
                    break;
                case POSSIBLY_SATISFIED:
                    realMatrix[i][lastMatrixColumnIndex] = "poss.sat";
                    break;
                default:
                    realMatrix[i][lastMatrixColumnIndex] = "unknown";
                    break;
            }
            i++;
        }
        declaredMatrix = realMatrix;
        return realMatrix;
    }
    
    public String updatedString(){
        String[][] updatedMatrix = updateNewMatrix();
        return getResult(updatedMatrix);
    }

    public void setConflictedConstraints(List<List<DataConstraint>> conflictedConstraints) {
        this.conflictedConstraints = conflictedConstraints;
    }
    
    public void setPermViolatedDataCon(List<Constraint> list) {
        this.permViolatedDataCon = list;
    }

    public void setPermViolatedCon(List<Constraint> list) {
        this.permViolatedCon = list;
    }

    private String convert(XTrace trace, int pos) {
        XExtendedEvent ev = XExtendedEvent.wrap(trace.get(pos));
        return "" + ev.getTimestamp().getTime();
    }

    private String getResult(final String[][] matrix) {
        String result = "[";
        int intCounterMin;
        int intCounterMax;
        String INF = "inf";
        for (int i = 0; i < matrix.length; i++) {// We have the constraints
            intCounterMin = 0;
            intCounterMax = 0;
            String oldStatus = matrix[i][0];
            for (int j = 0; j < matrix[0].length; j++) {
                if (matrix[i][j] == null) {
                    matrix[i][j] = oldStatus;
                }
                if ((j == (matrix[0].length - 1)) && (i == (matrix.length - 1))) {
                    if (!matrix[i][j].equals(oldStatus)) {
                        result = result + "mholds_for(status(" + constraintNames[i] + "," + oldStatus + "),["
                                + convert(trace, intCounterMin) + "," + convert(trace, intCounterMax) + "]),";
                        oldStatus = matrix[i][j];
                        intCounterMin = intCounterMax;
                    }
                    result = result + "mholds_for(status(" + constraintNames[i] + "," + matrix[i][j] + "),["
                            + convert(trace, intCounterMin) + "," + INF + "])]";
                    intCounterMin = intCounterMax;
                    intCounterMax++;
                } else {
                    if (j == (matrix[0].length - 1)) {
                        if (matrix[i][j].equals(oldStatus)) {
                            result = result + "mholds_for(status(" + constraintNames[i] + "," + matrix[i][j]
                                    + "),[" + convert(trace, intCounterMin) + "," + INF + "]),";
                        } else {
                            result = result + "mholds_for(status(" + constraintNames[i] + "," + oldStatus + "),["
                                    + convert(trace, intCounterMin) + "," + convert(trace, intCounterMax)
                                    + "])," + "mholds_for(status(" + constraintNames[i] + "," + matrix[i][j]
                                    + "),[" + convert(trace, intCounterMax) + "," + INF + "]),";
                        }
                    } else {
                        if (!matrix[i][j].equals(oldStatus)) {
                            result = result + "mholds_for(status(" + constraintNames[i] + "," + oldStatus + "),["
                                    + convert(trace, intCounterMin) + "," + convert(trace, intCounterMax)
                                    + "]),";
                            oldStatus = matrix[i][j];
                            intCounterMin = intCounterMax;
                        }
                    }
                }
                intCounterMax++;
            }
        }
        return result;
    }
}

