package core.alloy.codegen;

import core.helpers.RandomHelper;
import declare.DeclareParser;
import declare.DeclareParserException;

import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.deckfour.xes.extension.std.XConceptExtension;
import org.deckfour.xes.extension.std.XLifecycleExtension;
import org.deckfour.xes.extension.std.XTimeExtension;
import org.deckfour.xes.model.XAttribute;
import org.deckfour.xes.model.XAttributeMap;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.deckfour.xes.model.impl.XAttributeContinuousImpl;
import org.deckfour.xes.model.impl.XAttributeDiscreteImpl;
import org.deckfour.xes.model.impl.XAttributeLiteralImpl;
import org.deckfour.xes.model.impl.XAttributeMapImpl;

/**
 * Created by Vasiliy on 2018-05-10.
 */
public class NameEncoder {
    
    private Map<String, String> activityMapping;
    private Map<String, String> traceAttributeMapping;
    private Set<DataMappingElement> dataMapping;

    public NameEncoder() {
    	activityMapping = new HashMap<>();
        traceAttributeMapping = new HashMap<>();
        dataMapping = new HashSet<>();
    }

    public void createLogMapping(XLog xlog) {
    	
    	for (XTrace t : xlog) {
    		for (XAttribute traceAtt : t.getAttributes().values()) {
        		if (!traceAtt.getKey().equals(XConceptExtension.KEY_NAME)) {
        			String encoding = RandomHelper.getName();
        			traceAttributeMapping.put(encoding, traceAtt.toString());
        		}
        	}
        	
        	for (XEvent event : t) {
        		for (XAttribute eventAtt : event.getAttributes().values()) {
        			
        			if (eventAtt.getKey().equals(XConceptExtension.KEY_NAME) ) {
        				if( !activityMapping.containsValue(eventAtt.toString()) ) {
	        				String encoding = RandomHelper.getName();
	        				activityMapping.put(encoding, eventAtt.toString());
        				}
        				
        			} else if (!eventAtt.getKey().equals(XLifecycleExtension.KEY_TRANSITION) 
        					&& !eventAtt.getKey().equals(XTimeExtension.KEY_TIMESTAMP)) {
        				
        				Optional<DataMappingElement> optElem = dataMapping.stream()
    							.filter(item -> item.getOriginalName().equals(eventAtt.getKey()))
    							.findAny();
        				
        				if (optElem.isPresent()) {
        					DataMappingElement elem = optElem.get();
        					elem.addValues( Set.of(eventAtt.toString()) );
        					
        				} else {
        					
        					if (eventAtt instanceof XAttributeLiteralImpl) {
        						DataMappingElement newElem = new DataMappingElement(DataMappingElement.Type.LITERAL, eventAtt.getKey());
        						newElem.addValues(Set.of(eventAtt.toString()));
            					dataMapping.add(newElem);
            					
        					} else if (eventAtt instanceof XAttributeDiscreteImpl) {
        						DataMappingElement newElem = new DataMappingElement(DataMappingElement.Type.DISCRETE, eventAtt.getKey());
        						newElem.addValues(Set.of(eventAtt.toString()));
            					dataMapping.add(newElem);
            					
        					} else if (eventAtt instanceof XAttributeContinuousImpl) {
        						DataMappingElement newElem = new DataMappingElement(DataMappingElement.Type.CONTINUOUS, eventAtt.getKey());
        						newElem.addValues(Set.of(eventAtt.toString()));
            					dataMapping.add(newElem);
        					}
        				}
        			}
        		}
        	}
    	}
    }
    
    public void createDeclMapping(String declare) throws DeclareParserException {
    	String[] declareLines = DeclareParser.splitStatements(declare);
        
        List<String> dataLines = new ArrayList<>();
        for (String line : declareLines)
        	if (DeclareParser.isData(line))
        		dataLines.add(line);
        
        for (String line : declareLines) {
        	
        	if (DeclareParser.isTraceAttribute(line)) {
    			String name = line.substring("trace ".length()).trim();
    			
    			if (!traceAttributeMapping.values().contains(name)) {
	        		String encoding = RandomHelper.getName();
	        		traceAttributeMapping.put(encoding, name);
    			}
    		
        	
        	} else if (DeclareParser.isActivity(line)) {
        		String name = line.substring("activity ".length()).trim();
        		
        		if (!activityMapping.values().contains(name)) {
        			String encoding = RandomHelper.getName();
        			activityMapping.put(encoding, name);
        		}
        	
        	
        	} else if (DeclareParser.isDataBinding(line)) {
        		line = line.substring("bind ".length()).trim();
        		
        		for (Map.Entry<String, String> e : activityMapping.entrySet()) {
        			String actName = e.getValue();
        			if (line.startsWith(actName+": ")) {
        				/*
        				String dataNamesStr = line.substring((actName+": ").length());
        				List<String> possibleDataNames = extractAllPossibleDataNames(dataNamesStr);
        				List<String> trueNames = new ArrayList<>();
        				*/
        				List<String> trueNames = Arrays.asList(line.substring((actName+": ").length()).split(",\\s+"));
        				
        				for (String dl : dataLines) {
        					for (String name : trueNames) {	//for (String possibleName : possibleDataNames) {
        						if (dl.startsWith(name+": ")) {
    								//trueNames.add(possibleName);
        							Set<String> values = new HashSet<>( Arrays.asList( dl.substring((name+": ").length()).split(", ") ) );
    								values.forEach(value -> { value.trim(); });
    								
    								Optional<DataMappingElement> optElem = dataMapping.stream()
    														.filter(item -> item.getOriginalName().equals(name))
    														.findAny();

    								if (optElem.isPresent()) {
    									DataMappingElement elem = optElem.get();
    									
    									if (values.size() == 1
        										&& values.iterator().next().matches("(integer|float) between -?\\d+(\\.\\d+)? and -?\\d+(\\.\\d+)?")) {
        									
        									String[] split = values.iterator().next().split(" ");
        									values.clear();
        									values.add(split[2]);
        									values.add(split[4]);
        								}
    									
    									elem.addValues(values);
    								
    								} else {
    									DataMappingElement newElem;
    								
    									if (values.size() == 1) {
    										String singleValue = values.iterator().next();
	    									
    										if (singleValue.matches("float between -?\\d+(\\.\\d+)? and -?\\d+(\\.\\d+)?")) {
	    										newElem = new DataMappingElement(DataMappingElement.Type.CONTINUOUS, name);
	    										String[] split = singleValue.split(" ");
	    										newElem.addValues(Set.of(split[2], split[4]));
	    										
	    									} else if (singleValue.matches("integer between -?\\d+ and -?\\d+")) {
	    										newElem = new DataMappingElement(DataMappingElement.Type.DISCRETE, name);
	    										String[] split = singleValue.split(" ");
	    										newElem.addValues(Set.of(split[2], split[4]));
	    										
	    									} else {
	    										newElem = new DataMappingElement(DataMappingElement.Type.LITERAL, name);
	    										newElem.addValues(values);
	    									}
	    								
	    								} else {
	    									newElem = new DataMappingElement(DataMappingElement.Type.LITERAL, name);
											newElem.addValues(values);
	    								}
	    								
	    								dataMapping.add(newElem);
    								}
        						}
        					}
        				}
        					
        				break;
        			}
        		}
        	}
        }
    }
    
    public String encodeDeclModel(String declare) throws DeclareParserException {
    	String[] declareLines = DeclareParser.splitStatements(declare);
        
        List<String> dataLines = new ArrayList<>();
        for (String line : declareLines)
        	if (DeclareParser.isData(line))
        		dataLines.add(line);
        
        String encodedDeclare = "";
        
        for (String line : declareLines) {
        	String encodedLine = "";
        	
        	if (DeclareParser.isTraceAttribute(line)) {
        		String name = line.substring("trace ".length()).trim();
        		
        		for (Map.Entry<String, String> entry : traceAttributeMapping.entrySet())
        			if ( name.equals(entry.getValue()) )
        				encodedLine = "trace " + entry.getKey();
        	
        	
        	} else if (DeclareParser.isActivity(line)) {
        		String name = line.substring("activity ".length()).trim();
        		
        		for (Map.Entry<String, String> entry : activityMapping.entrySet())
        			if ( name.equals(entry.getValue()) )
        				encodedLine = "activity " + entry.getKey();
    			
        	
        	} else if (DeclareParser.isDataBinding(line)) {
        		line = line.substring("bind ".length()).trim();
        		
        		for (Map.Entry<String, String> e : activityMapping.entrySet()) {
        			String actName = e.getValue();
        			if (line.startsWith(actName+": ")) {
        				
        				List<String> trueNames = Arrays.asList(line.substring((actName+": ").length()).split(",\\s+"));
        				
        				List<String> trueEncodings = new ArrayList<>();
        				for (DataMappingElement dme : dataMapping)
        					if (trueNames.contains(dme.getOriginalName()))
        						trueEncodings.add(dme.getEncodedName());
        					
        				encodedLine = "bind " + e.getKey() + ": " + String.join(", ", trueEncodings);
        				break;
        			}
        		}
        	
        		
        	} else if (DeclareParser.isData(line)) {
        		for (DataMappingElement dme : dataMapping)
        			if (line.startsWith(dme.getOriginalName()+": ")) {
        				encodedLine = dme.getEncodedName() + ": " + String.join(", ", dme.getValuesMapping().keySet());
        				break;
        			}
        	
        	
        	} else if (DeclareParser.isDataConstraint(line)) {
        		// Encoding templates of the data constraint
            	Pattern templatePattern = Pattern.compile(".*\\[.*\\]\\s*");
            	Matcher mTempl = templatePattern.matcher(line);
            	if (mTempl.find()) {
            		String templateStr = mTempl.group().trim();
            		String templateName = templateStr.substring(0, templateStr.indexOf('['));
            		String[] activities = templateStr.substring(templateStr.indexOf('[')+1, templateStr.lastIndexOf(']')).split(",\\s+");
            		
            		String n = null;
            		if (templateName.matches("Absence|Existence|Exactly") && activities.length > 1) {
            			n = activities[activities.length-1];
            			activities = Arrays.copyOf(activities, activities.length-1);
            		}
            		
            		List<String> encodedActivityNames = new ArrayList<>();
            		for (String act : activities)
            			for (Map.Entry<String, String> e : activityMapping.entrySet())
            				if (e.getValue().equals(act))
            					encodedActivityNames.add(e.getKey());
            		
            		if (n != null)
            			encodedActivityNames.add(n);
            		
            		encodedLine += templateName + "[" + String.join(", ", encodedActivityNames) + "] ";
            		line = line.substring(templateStr.length()).trim();
            	}
            	
            	// Encoding condition of the data constraint
            	Pattern conditionPattern = Pattern.compile("\\|[^\\|]*");
            	Matcher mCond = conditionPattern.matcher(line);
            	while (mCond.find()) {
            		String singleCondition = mCond.group().substring(1);	// Removing the initial pipe character
            		encodedLine += "|" + encodeCondition(singleCondition) + " ";
            	}
        	
        	
        	} else if (DeclareParser.isConstraint(line)) {
        		continue;	// ???????????????????????????????????
        	}
        	
        	encodedDeclare += encodedLine.isEmpty() ? "" : encodedLine + "\n" ;
        }
        
    	return encodedDeclare;
    }
    
    private String encodeCondition(String condition) throws DeclareParserException {
    	String encodedCondition = "";
    	Pattern parenthesesPattern = Pattern.compile("(?=\\()(?:(?=.*?\\((?!.*?\\1)(.*\\)(?!.*\\2).*))(?=.*?\\)(?!.*?\\2)(.*)).)+?.*?(?=\\1)[^(]*(?=\\2$)");
    	
    	if (condition.matches("\\d+,\\d+,\\w")) // Time conditions will be left as they are
    		return condition;
    	
    	String lastFound = "Logical Operator";
    	while(!condition.isBlank()) {
    		condition = condition.trim();
    		
    		if (lastFound.equals("Logical Operator")) {
				lastFound = "Predicate";
				
				if (condition.charAt(0) == '(') {	// It implies the presence of a predicate in parentheses
					Matcher m = parenthesesPattern.matcher(condition);
					if (m.find()) {
						encodedCondition += "(" + encodeCondition(condition.substring(m.start()+1, m.end()-1)) + ")";
						condition = condition.substring( m.end()<condition.length() ? m.end()+1 : m.end() );
					}
					
				} else {
					AbstractMap.SimpleEntry<String, String> mapping = encodeNextPredicate(condition);
					encodedCondition += " " + mapping.getKey() + " ";
					condition = condition.substring(mapping.getValue().length());
				}
				
			} else {	// The last string found in the condition should be a predicate
				lastFound = "Logical Operator";
				
				if (condition.toLowerCase().startsWith("and")) {
					encodedCondition += " and ";
					condition = condition.substring(3);
				} else if (condition.toLowerCase().startsWith("or")) {
					encodedCondition += " or ";
					condition = condition.substring(2);
				}
			}
    	}
    	
    	return encodedCondition.replaceAll("\\s+", " ").trim();
    }
    
    private AbstractMap.SimpleEntry<String, String> encodeNextPredicate(String condition) throws DeclareParserException {
    	// Dealing with unary predicates   <==>   UnaryOperator AttributeName
    	if (condition.indexOf(' ') >= 0
			&& condition.substring(0, condition.indexOf(' ')).matches("(?i)same|different|not|exist") 
			&& dataMapping.stream().anyMatch(item -> condition.substring(condition.indexOf(' ')).trim().startsWith(item.getOriginalName()) )) {
    		
    		String unaryOperator = condition.substring(0, condition.indexOf(' '));
    		DataMappingElement attributeMapping = dataMapping.stream()
    								.filter(item -> condition.substring(condition.indexOf(' ')).trim().startsWith(item.getOriginalName()))
    								.findFirst().get();
    		
    		return new AbstractMap.SimpleEntry<>
    								(unaryOperator + " " + attributeMapping.getEncodedName(),
    								 unaryOperator + " " + attributeMapping.getOriginalName());
    	
    	// Dealing with binary predicates   <==>   (A|B).AttributeName BinaryOperator AttributeValue|(AttrVal1, ..., AttrValN)
    	} else if ( dataMapping.stream().anyMatch(item -> condition.substring(2).startsWith(item.getOriginalName())) ) {
    		
    		// There can be more than one attribute matching the initial part of the condition, the one with the longest name will be selected
    		List<DataMappingElement> matchedAttributes = dataMapping.stream()
									.filter(item -> condition.substring(2).startsWith(item.getOriginalName()))
									.collect(Collectors.toList());
    		
    		DataMappingElement longestAttribute = matchedAttributes.stream()
				    				.map(item -> new AbstractMap.SimpleEntry<>(item, item.getOriginalName().length()) )
				    				.max( Comparator.comparingInt(AbstractMap.SimpleEntry::getValue) )
				    				.get().getKey();
    		
    		String attributeName = condition.substring(0, 2) + longestAttribute.getOriginalName();
    		String encodedName = condition.substring(0, 2) + longestAttribute.getEncodedName();
    		
    		String reducedCondition = condition.substring(attributeName.length());
    		
    		boolean blankBeforeOperator = reducedCondition.charAt(0) == ' ';
    		reducedCondition = reducedCondition.trim();
    			
    		String operator = getOperatorFromCondition(reducedCondition);
    		
    		reducedCondition = reducedCondition.substring(operator.length());
    		
    		boolean blankAfterOperator = reducedCondition.charAt(0) == ' ';
    		reducedCondition = reducedCondition.trim();
    		
    		if (operator.matches("(?i)(not )?in")) {
	    		List<String> attributeValues = getValuesInSet(reducedCondition);
	    		
	    		List<String> encodedValues = new ArrayList<>();
	    		for (String value : attributeValues)
	    			for (Map.Entry<String, String> e : longestAttribute.getValuesMapping().entrySet())
	    				if (e.getValue().equals(value))
	    					encodedValues.add(e.getKey());
	    		
	    		return new AbstractMap.SimpleEntry<>
										(encodedName+" "+operator+" "+"("+String.join(", ", encodedValues)+")",
					    				 attributeName+(blankBeforeOperator?" ":"")+operator+(blankAfterOperator?" ":"")+"("+String.join(", ", attributeValues)+")");
    		
    		} else {
    			String attributeValue, encodedValue;
    			
    			if (longestAttribute.getType() != DataMappingElement.Type.LITERAL) {
    				Pattern numPattern = Pattern.compile("-?\\d+(\\.\\d+)?");
    				Matcher m = numPattern.matcher(reducedCondition);
    				m.find();
					String number = m.group();
					
					attributeValue = number;
					encodedValue = number;
    				
    			} else {
    				String temp = reducedCondition;
    	    		Map.Entry<String, String> valueMapping = longestAttribute.getValuesMapping().entrySet().stream()
    	    													.filter(entry -> temp.startsWith(entry.getValue()))
    	    													.findFirst().get();
    	    		
    	    		attributeValue = valueMapping.getValue();
    	    		encodedValue = valueMapping.getKey();
    			}
    			
	    		return new AbstractMap.SimpleEntry<>
					    				(encodedName+" "+operator+" "+encodedValue, 
					    				 attributeName+(blankBeforeOperator?" ":"")+operator+(blankAfterOperator?" ":"")+attributeValue);
    		}
    	}
    	
    	throw new DeclareParserException("An error is occurred while encoding a predicate in a condition");
    }
    
    private String getOperatorFromCondition(String condition) throws DeclareParserException {
    	condition = condition.replaceAll("\\s+", " ");
    	StringBuilder charBuffer = new StringBuilder();
    	
    	int charInd=0;
    	while (charInd < condition.length()) {
    		charBuffer.append(condition.charAt(charInd));
    		String op = charBuffer.toString();
    		
    		if (op.matches("(?i)=|!=|(not )?in")) {
				return op;
				
			} else if (op.matches("<|>")) {
				if (charInd+1 < condition.length()) {
					op += condition.charAt(charInd+1);
					
					if (op.matches("<=|>="))
						return op;
					else
						return charBuffer.toString();
				
				} else
					return op;
				
			} else if (op.matches("(?i)is")) {
				if (charInd+4 < condition.length()) {
					for (int i=charInd+1; i<=charInd+4; i++)
						op += condition.charAt(i);
					
					if (op.matches("(?i)is not"))
						return op;
					else
						return charBuffer.toString();
					
				} else
					return op;
			}
    		
    		charInd++;
    	}
    	
    	throw new DeclareParserException("No operator was found while parsing a declare condition.");
    }
    
    private List<String> getValuesInSet(String condition) {
    	int parenthesesBalance = 0;
    	
    	int charInd = 0;
    	for (char ch : condition.toCharArray()) {
    		if (ch == '(')
    			parenthesesBalance++;
    		else if (ch == ')')
    			parenthesesBalance--;
    		
    		if (parenthesesBalance == 0)
    			break;
    		
    		charInd++;
    	}
    	
    	String valuesStr = condition.substring(1, charInd);
    	List<String> values = Arrays.asList(valuesStr.split(", ")); 
    	values.forEach(val -> { val.trim(); });
    	
    	return values;
    }
    
    /* === FRANCESCO ===
     * With this part I tried to improve the name extraction from .decl files by overcoming the limitations in names,
     * e.g. the fact that only names without ", ", ": " and "|" char sequences are allowed.
     * But it is incomplete...
    
    private List<String> extractAllPossibleDataNames(String dataNamesStr) {
    	Set<String> possibleNames = new HashSet<>();
    	
    	possibleNames.add(dataNamesStr);	// The whole string can be a single data name
    	
    	List<String> singleNames = Arrays.asList(dataNamesStr.split(", "));
    	
    	if (singleNames.size() > 1) {
	    	possibleNames.addAll(singleNames);
	    	
	    	for (String[] pair : getNameDispositions(singleNames))
	    		possibleNames.addAll(Arrays.asList(pair));
    	}
    	
    	return new ArrayList<>(possibleNames);
    }
    
    private Set<String[]> getNameDispositions(List<String> singleNames) {
    	Set<String[]> dispositions = new HashSet<>();
    	
    	List<String> firstNames = new ArrayList<>();
    	while (!singleNames.isEmpty()) {
    		firstNames.add(singleNames.remove(0));
    		String[] pair = new String[] {String.join(", ", firstNames), String.join(", ", singleNames) };
    		dispositions.add(pair);
    	}
    	
    	return dispositions;
    }
    */
    
    public XTrace encodeTrace(XTrace t) {
    	for (XEvent evt : t) {
        	String eventName = XConceptExtension.instance().extractName(evt);
        	
        	Optional<String> encodedName = activityMapping.entrySet().stream()
					.filter(entry -> entry.getValue().equals(eventName))
					.map(Map.Entry::getKey)
					.findFirst();
        	
        	if (encodedName.isPresent()) {
        		XAttributeMap attributes = new XAttributeMapImpl();
        		
        		for (Map.Entry<String,XAttribute> entry : evt.getAttributes().entrySet()) {
        			
        			if (entry.getKey().equals(XConceptExtension.KEY_NAME)) {
        				attributes.put(entry.getKey(), 
        						new XAttributeLiteralImpl(entry.getValue().getKey(), encodedName.get(), entry.getValue().getExtension()) );
        			
        			} else if ( dataMapping.stream()
    								.map(DataMappingElement::getOriginalName)
    								.anyMatch(item -> item.equals(entry.getKey())) ) {
        				
        				DataMappingElement dme = dataMapping.stream()
        						.filter(item -> item.getOriginalName().equals(entry.getKey()))
        						.findFirst().get();
        				
        				switch (dme.getType()) {
						case CONTINUOUS:
							attributes.put(dme.getEncodedName(),
    								new XAttributeContinuousImpl(dme.getEncodedName(), Double.parseDouble(entry.getValue().toString()), entry.getValue().getExtension()) );
							break;
						
						case DISCRETE:
							attributes.put(dme.getEncodedName(),
    								new XAttributeDiscreteImpl(dme.getEncodedName(), Long.parseLong(entry.getValue().toString()), entry.getValue().getExtension()) );
							break;
						
						case LITERAL:
							String encodedValue = dme.getValuesMapping().entrySet().stream()
											.filter(e -> e.getValue().equals(entry.getValue().toString()))
											.map(Entry::getKey)
											.findFirst().get();
					
							attributes.put(dme.getEncodedName(), 
    						new XAttributeLiteralImpl(dme.getEncodedName(), encodedValue, entry.getValue().getExtension()) );
							
							break;
						}
        			
        			} else {
        				attributes.put(entry.getKey(), entry.getValue());
        			}
        		}
            	
        		evt.setAttributes(attributes);
        		
        	} else {
            	return null;
            }
        }
    	
    	return t;
    }
    
    public Map<String, String> getActivityMapping() {
		return activityMapping;
	}

	public Map<String, String> getTraceAttributeMapping() {
		return traceAttributeMapping;
	}

	public Set<DataMappingElement> getDataMapping() {
		return dataMapping;
	}

	public static class DataMappingElement {
    	
		public enum Type {LITERAL, DISCRETE, CONTINUOUS}
		
		Type type;
		String originalName;
    	String encodedName;
    	Set<String> numericValues;
    	Map<String, String> literalValuesMapping;
    	
    	public DataMappingElement(Type type, String originalName) { //, Set<String> values) {
    		this.type = type;
    		this.originalName = originalName;
    		this.encodedName = RandomHelper.getName();
    		
    		if (this.type == Type.LITERAL)
    			this.literalValuesMapping = new HashMap<>();
    		else
    			this.numericValues = new HashSet<>();
    	}
    	
    	public Type getType() {
    		return type;
    	}
    	
		public String getOriginalName() {
			return originalName;
		}

		public String getEncodedName() {
			return encodedName;
		}

		public Map<String, String> getValuesMapping() {
    		Map<String, String> output = new HashMap<>();

			switch (type) {
			case CONTINUOUS: {
				OptionalDouble min = numericValues.stream().mapToDouble(num -> Double.parseDouble(num)).min();
				OptionalDouble max = numericValues.stream().mapToDouble(num -> Double.parseDouble(num)).max();
				
				if (min.isPresent() && max.isPresent())
					output.put("float between " + min.getAsDouble() + " and " + max.getAsDouble(), "float between " + min.getAsDouble() + " and " + max.getAsDouble());
				
				break;
			}
			
			case DISCRETE: {
				OptionalInt min = numericValues.stream().mapToInt(num -> Integer.parseInt(num)).min();
				OptionalInt max = numericValues.stream().mapToInt(num -> Integer.parseInt(num)).max();
				
				if (min.isPresent() && max.isPresent())
					output.put("integer between " + min.getAsInt() + " and " + max.getAsInt(), "integer between " + min.getAsInt() + " and " + max.getAsInt());
				
				break;
			}
			
			case LITERAL:
				output = literalValuesMapping;
				break;
			}
			
			return output;
		}
		
		public void addValues(Set<String> values) {
			switch (type) {
			case CONTINUOUS:
			case DISCRETE:
				this.numericValues.addAll(values);
				break;
				
			case LITERAL:
				values.forEach(val -> {
					if (!literalValuesMapping.values().contains(val))
						literalValuesMapping.put(RandomHelper.getName(), val);
				});        			
				break;
			}
		}
		
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((originalName == null) ? 0 : originalName.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			DataMappingElement other = (DataMappingElement) obj;
			if (originalName == null) {
				if (other.originalName != null)
					return false;
			} else if (!originalName.equals(other.originalName))
				return false;
			return true;
		}
    }
}
