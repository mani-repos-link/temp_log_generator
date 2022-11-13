package core.monitoring;

import edu.mit.csail.sdg.alloy4.Err;
import declare.DeclareParserException;
import core.Global;
import core.alloy.codegen.NameEncoder;
import core.alloy.codegen.NameEncoder.DataMappingElement;
import core.exceptions.GenerationException;
import declare.DeclareModel;

import org.deckfour.xes.extension.std.XConceptExtension;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XTrace;
import org.deckfour.xes.model.impl.XAttributeMapImpl;
import org.deckfour.xes.model.impl.XTraceImpl;
import org.processmining.operationalsupport.xml.OSXMLConverter;

import declare.DeclareParser;

import java.util.ArrayList;
import java.util.Map;

public class MonitorRunner {
	
	private boolean conflict;
	private NameEncoder encoder;
	
	private XTraceImpl oneTrace;
	private OSXMLConverter osxmlConverter;
	
	private DeclareModel model;
	private int overallNumberOfConstraints;
	private ConstraintChecker constraintChecker;
	
	private int r = 2;
	
	public MonitorRunner(boolean conflict, String stringModel) throws DeclareParserException {
        this.conflict = conflict;
        this.encoder = new NameEncoder();
        this.oneTrace = new XTraceImpl(new XAttributeMapImpl());
        this.osxmlConverter = new OSXMLConverter();
        
        String modelWithDummyStart = "activity complete" + System.lineSeparator() + stringModel;
        if (Global.encodeNames) {
        	encoder.createDeclMapping(modelWithDummyStart);
        	modelWithDummyStart = encoder.encodeDeclModel(modelWithDummyStart);
        }
        this.model = DeclareParser.parse(modelWithDummyStart);
        
        this.overallNumberOfConstraints = model.getDataConstraints().size() + model.getConstraints().size();
        this.constraintChecker = new ConstraintChecker(model);
        //constraintChecker.setEncodings(encoder);  // Used only for printing 
	}

    public String setTrace(String stringTrace) throws DeclareParserException, GenerationException, Err {
        XTrace t = (XTrace) osxmlConverter.fromXML(stringTrace);
        t = encoder.encodeTrace(t);
        
        try {
        	XEvent e = t.get(0);
        	oneTrace.add(e);
        	
            constraintChecker.setTrace(oneTrace);
            if (oneTrace.size() == 1)
                constraintChecker.initMatrix();
            
            String answer;
            
            String encodedDummyEvent = encoder.getActivityMapping().entrySet().stream()
					.filter(entry -> entry.getValue().equals("complete"))
					.map(Map.Entry::getKey)
					.findFirst().get();
            
            if (XConceptExtension.instance().extractName(e).equals(encodedDummyEvent)) {
                oneTrace.add(e);
                constraintChecker.setTrace(oneTrace);
                if (conflict)
                    constraintChecker.run();
                
                constraintChecker.setFinal();
                
                // Restart things
                oneTrace = new XTraceImpl(new XAttributeMapImpl());
                constraintChecker.setConflictedConstraints(new ArrayList<>());
                //constraintChecker.setPermViolatedCon(new ArrayList<>());
                
            } else {
                
                constraintChecker.run();
                
                if (conflict) {
                    boolean subListCheck = constraintChecker.checkFullConjuction(); // here we find out whether we need to do any further investigation. kui see on false
                    boolean inWhile = !subListCheck; // siis see on true, ja kui eelnev on true, siis see on false ja see on ka see, mida me tahame, sest eelmine konjuktsioon on ok.
                    
                    while (inWhile && ((r <= (overallNumberOfConstraints)) || overallNumberOfConstraints == 2)) { // mis t�hendab, et siinolemine on ull timm
                        inWhile = constraintChecker.checkSublistConjunction(overallNumberOfConstraints, r);
                        
                        if (overallNumberOfConstraints == 2)
                            break;
                        
                        r++;
                    }
                    
                    r = 2; // should be two again, so that we would check the value again afterwards
                }
            }
            
            answer = constraintChecker.updatedString();
            
            // Restoring real (decoded) names 
            for (Map.Entry<String,String> entry : encoder.getActivityMapping().entrySet())
            	answer = answer.replace(entry.getKey(), entry.getValue());
            
            for (DataMappingElement d : encoder.getDataMapping()) {
            	answer = answer.replace(d.getEncodedName(), d.getOriginalName());
            	
            	for (Map.Entry<String,String> entry : d.getValuesMapping().entrySet())
            		answer = answer.replace(entry.getKey(), entry.getValue());
            }
            
            return answer;
            
        } catch (Throwable err) {
            err.printStackTrace();
            return null;
        }
    }
}
