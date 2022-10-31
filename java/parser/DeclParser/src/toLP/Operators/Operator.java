package toLP.Operators;


import java.util.AbstractMap;
import java.util.Map;

public interface Operator {

	public static Map<Operator, Operator> oppositeOperator = Map.ofEntries(
					new AbstractMap.SimpleEntry<Operator, Operator>(LogicalOperator.AND, LogicalOperator.OR),
					new AbstractMap.SimpleEntry<Operator, Operator>(LogicalOperator.OR, LogicalOperator.AND),
					new AbstractMap.SimpleEntry<Operator, Operator>(AttributeOperator.GT, AttributeOperator.LEQ),
					new AbstractMap.SimpleEntry<Operator, Operator>(AttributeOperator.LEQ, AttributeOperator.GT),
					new AbstractMap.SimpleEntry<Operator, Operator>(AttributeOperator.LT, AttributeOperator.GEQ),
					new AbstractMap.SimpleEntry<Operator, Operator>(AttributeOperator.GEQ, AttributeOperator.LT),
					new AbstractMap.SimpleEntry<Operator, Operator>(AttributeOperator.EQ, AttributeOperator.NEQ),
					new AbstractMap.SimpleEntry<Operator, Operator>(AttributeOperator.NEQ, AttributeOperator.EQ),
					new AbstractMap.SimpleEntry<Operator, Operator>(AttributeOperator.IS, AttributeOperator.IS_NOT),
					new AbstractMap.SimpleEntry<Operator, Operator>(AttributeOperator.IS_NOT, AttributeOperator.IS),
					new AbstractMap.SimpleEntry<Operator, Operator>(AttributeOperator.IN, AttributeOperator.NOT_IN),
					new AbstractMap.SimpleEntry<Operator, Operator>(AttributeOperator.NOT_IN, AttributeOperator.IN),
					new AbstractMap.SimpleEntry<Operator, Operator>(AttributeOperator.SAME, AttributeOperator.DIFFERENT),
					new AbstractMap.SimpleEntry<Operator, Operator>(AttributeOperator.DIFFERENT, AttributeOperator.SAME),
					new AbstractMap.SimpleEntry<Operator, Operator>(AttributeOperator.EXIST, AttributeOperator.EXIST) ); //TODO: Note that the AttributeOperator EXIST hasn't an opposite operator!

	public String getStringDisplay();

	public String getTextualDisplay();

	public String getDeclareDisplay();

	public static Operator getOpposite(Operator op) {
		return oppositeOperator.get(op);
	}

	public static Operator getOperatorFromString(String opStr) {
		for (Operator op : oppositeOperator.keySet())
			if (opStr.equals(op.getStringDisplay()))
				return op;

		return null;
	}
}
