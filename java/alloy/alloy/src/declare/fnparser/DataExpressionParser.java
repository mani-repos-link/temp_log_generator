package src.declare.fnparser;


import src.declare.DeclareParserException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Created by Vasiliy on 2017-10-19.
 */
public final class DataExpressionParser {
    private static String opTokenRegex = "(?i)(\\s+(is\\s+not|is|not\\s+in|in|or|and))|(\\s*(not|same|different|exist))\\s+"; 	// (?i) means case-insensitive
    private static Pattern opTokenPattern = Pattern.compile(opTokenRegex);
    
    private static String numTokenRegex = "-?\\d+(\\.\\d+)?";
    private static Pattern numTokenPattern = Pattern.compile(numTokenRegex);
    
    private static String varTokenRegex = "(?!"+numTokenRegex+")\\S+\\.\\S+";
    private static Pattern varTokenPattern = Pattern.compile(varTokenRegex);
    
    private static String taskTokenRegex = "\\S+";
    private static Pattern taskTokenPattern = Pattern.compile(taskTokenRegex);
    
    private static String groupTokenRegex = "(?=\\()(?:(?=.*?\\((?!.*?\\1)(.*\\)(?!.*\\2).*))(?=.*?\\)(?!.*?\\2)(.*)).)+?.*?(?=\\1)[^(]*(?=\\2$)";
    private static Pattern groupTokenPattern = Pattern.compile(groupTokenRegex);
    
    private static String compTokenRegex = "<=|>=|<|>|=|!=";
    private static Pattern compTokenPattern = Pattern.compile(compTokenRegex);
    
    private static String placeholderRegex = "\\?";
    private static Pattern placeholderPattern = Pattern.compile(placeholderRegex);

    private static Pattern tokenPattern = Pattern.compile(groupTokenRegex + "|" + opTokenRegex + "|" + compTokenRegex  
												+ "|" + numTokenRegex + "|" + varTokenRegex + "|" + taskTokenRegex 
												+ "|" + placeholderRegex);

    private DataExpressionParser() {
        throw new AssertionError();
      }
    
    public static DataExpression parse(String condition) throws DeclareParserException {
    	List<Token> tokens = parseTokens(condition);
        return buildExpressionTree(tokens);
    }

    private static List<Token> parseTokens(String conditionString) throws DeclareParserException {
        List<Token> tokens = new ArrayList<>();
        
        int index = 0;
        Matcher m = tokenPattern.matcher(conditionString);
        
        while (m.find())
        	tokens.add(createToken(index++, m.group()));
        
        for (Token t : tokens)
        	if (t.getType() == Token.Type.Group)
        		if (t.getValue().chars().filter(ch -> ch == '(').count() != t.getValue().chars().filter(ch -> ch == ')').count())
                    throw new DeclareParserException("Unbalanced parentheses in token: \"" + t.getValue() + "\" of condition \"" + conditionString + "\"");	// TODO: write erroneous line of code

        return tokens;
    }
    
    private static Token createToken(int i, String value) throws DeclareParserException {
    	// Order matters!

    	if (groupTokenPattern.matcher(value).matches())
            return new Token(i, Token.Type.Group, value);
    	
    	if (opTokenPattern.matcher(value).matches())
            return new Token(i, Token.Type.Operator, value.trim().toLowerCase());
    	
    	if (compTokenPattern.matcher(value).matches())
            return new Token(i, Token.Type.Comparator, value);
    	
    	if (numTokenPattern.matcher(value).matches())
            return new Token(i, Token.Type.Number, value);
    	
    	if (varTokenPattern.matcher(value).matches())
            return new Token(i, Token.Type.Variable, value);
    	
    	if (taskTokenPattern.matcher(value).matches())
            return new Token(i, Token.Type.Activity, value);
    	
    	if (placeholderPattern.matcher(value).matches())
            return new Token(i, Token.Type.R, value);
    	
        throw new DeclareParserException("unknown token: " + value);    // TODO: write erroneous line of code
    }
    
    private static DataExpression buildExpressionTree(List<Token> tokens) throws DeclareParserException {
        
    	if (tokens.isEmpty())	// Empty expression evaluates to true
            return new ValueExpression(new Token(0, Token.Type.Activity, "True[]"));   
    	
    	tokens = unrollNotEqualsTokens(tokens);
    	
    	// Parsing "and", "or" logical operators at first
    	for (Token tkn : tokens) {
    		switch (tkn.getValue().toLowerCase().replaceAll("\\s+"," ").strip()) {
    		case "or":
    		case "and":
    			return new BinaryExpression(tkn, getLeft(tokens, tkn.getPosition()), getRight(tokens, tkn.getPosition()));
    		}
    	}
    	
    	// Then, parsing comparators and remaining operators
        for (Token tkn : tokens) {
        	if (tkn.getType() == Token.Type.Comparator) {
        		return new BinaryExpression(tkn, getLeft(tokens, tkn.getPosition()), getRight(tokens, tkn.getPosition()));
        	
        	} else if (tkn.getType() == Token.Type.Operator) {
        		switch (tkn.getValue().toLowerCase().replaceAll("\\s+"," ").strip()) {
        		// Unary operators
        		case "not":
        		case "same":
        		case "different":
        		case "exist":
        			return new UnaryExpression(tkn, getRight(tokens, tkn.getPosition()));
        		
        		// Binary operators
        		case "is":
        		case "is not":
        		case "in":
        		case "not in":
        			return new BinaryExpression(tkn, getLeft(tokens, tkn.getPosition()), getRight(tokens, tkn.getPosition()));
        			
        		default:
        			throw new DeclareParserException("Unhandled token operator: " + tkn.getValue());
				}
        	}
        }
        
        String setTokenRegex = "\\((\\S+,\\s+)*\\S+\\)";
        // Parsing remaining tokens at last
        for (Token tkn : tokens) {
        	switch (tkn.getType()) {
			case Group:
				// Since Group type matches everything is surrounded by parentheses, it matches also Set type.
	        	// So, before recursion, it has to be checked if the Group is nothing more than a Set.
				if (tkn.getValue().matches(setTokenRegex))
					return new ValueExpression(new Token(tkn.getPosition(), Token.Type.Set, tkn.getValue()));
				
				// Recursive call
				return parse(tkn.getValue().substring(1, tkn.getValue().length()-1)); // Cutting the surrounding parentheses
			
			case Activity:
			case Number:
			case Variable:
			case R:
				return new ValueExpression(tkn);

			default:
				throw new DeclareParserException("Unhandled token type: " + tkn.getType());
			}
        }

        throw new DeclareParserException(String.join(", ", tokens.stream().map(Object::toString).collect(Collectors.toList())));
    }
    
    private static List<Token> unrollNotEqualsTokens(List<Token> tokens) {
    	List<Token> notEqTokens = tokens.stream()
    									.filter(tkn -> tkn.getType().equals(Token.Type.Comparator) && tkn.getValue().equals("!="))
    									.collect(Collectors.toList());
    	
    	/*
    	 * A "!=" token can be seen as a conjunction of two tokens "<" and ">" with the same operands.
    	 * For example: 				(x != 1)    <==>    (x < 1) OR (x > 1)
    	 */
    	if (!notEqTokens.isEmpty()) {
    		// Iterating in reversed order to maintain the valid position references of the single not-equals tokens
    		for (Token neqTkn : notEqTokens) {
    			Token precOperand = tokens.get(tokens.indexOf(neqTkn)-1);
    			Token succOperand = tokens.get(tokens.indexOf(neqTkn)+1);
    			
    			// Grouping the unrolled tokens
    			Token conj = new Token(0, Token.Type.Group, "(" + precOperand.getValue() + " < " + succOperand.getValue() + " or "
    															+ precOperand.getValue() + " > " + succOperand.getValue() + ")" );
    			tokens.set(tokens.indexOf(precOperand), conj);
    			tokens.remove(tokens.indexOf(neqTkn));
    			tokens.remove(tokens.indexOf(succOperand));
    		}
    		
    		// Updating the token positions
	        tokens.forEach(elem -> elem.setPosition(tokens.indexOf(elem)) );
    	}
    	
    	return tokens;
    }

    private static DataExpression getLeft(List<Token> tokens, int position) throws DeclareParserException {
        return buildExpressionTree(tokens.subList(0, position));
    }

    private static DataExpression getRight(List<Token> tokens, int position) throws DeclareParserException {
        List<Token> sub = tokens.subList(position + 1, tokens.size());
        sub.forEach(i -> i.setPosition(i.getPosition() - position - 1));
        return buildExpressionTree(sub);
    }

    public static void retrieveNumericExpressions(Map<String, List<DataExpression>> map, DataExpression expr) throws DeclareParserException {
        if (expr.getNode().getType() == Token.Type.Comparator) {
        	BinaryExpression binExpr = (BinaryExpression) expr;
        	
        	if (binExpr.getLeft().getNode().getType() == Token.Type.Number
        			^ binExpr.getRight().getNode().getType() == Token.Type.Number) {
        		
        		String var;
        		if (binExpr.getLeft().getNode().getType() == Token.Type.Number)
        			var = binExpr.getRight().getNode().getValue();
        		else
        			var = binExpr.getLeft().getNode().getValue();
        		
        		var = var.substring(var.indexOf('.') + 1);
                if (!map.containsKey(var))
                    map.put(var, new ArrayList<>());
                map.get(var).add(expr);
        		
                return;
        	}
        }

        if (expr instanceof UnaryExpression) {
            retrieveNumericExpressions(map, ((UnaryExpression) expr).getValue());

        } else if (expr instanceof BinaryExpression) {
            retrieveNumericExpressions(map, ((BinaryExpression) expr).getLeft());
            retrieveNumericExpressions(map, ((BinaryExpression) expr).getRight());
        }
    }
}
