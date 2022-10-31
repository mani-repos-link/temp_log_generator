package toLP;

import Declare.DeclareParser;
import toLP.Operators.AttributeOperator;
import toLP.Operators.LogicalOperator;
import toLP.Predicates.*;

import javax.naming.OperationNotSupportedException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Decl2LP {

	/*
	Since ASP isn't able to handle floating attributes (infinite values in a range), here they are
	discretized with a precision equals to the least significant floating digit of the range bounds and predicate values too.

	E.g.:
	The line "attribute: float between 1.120 and 2.0" will be treated as "attribute: integer between 112 and 200",
	then each computed integer will be scaled again to the correct floating that it represents.
	Note that if a predicate "attribute > 1.808" was present, then the values will be scaled by 3 digits,
	for example the data line would be "attribute: integer between 1120 and 2000".

	Below, the map floatingAttributes contains entries formed by the name of the attribute and the number
	of significat digit to scale.
	*/
	private static Map<String, Integer> floatingAttributes;

	public static String decl2lp(String declModel) {

		StringBuilder lpBuilder = new StringBuilder();
		floatingAttributes = new HashMap<>();
		Pattern constraintPattern = Pattern.compile("(.*)\\[(.*)\\]\\s*(.*)");
		int constraintIndex = 0;

		try {
			String[] lines = declModel.split("\\r?\\n");

			for (String line : lines) {
				if (DeclareParser.isActivity(line)) {
					String act = line.substring("activity ".length());
					lpBuilder.append("activity(").append(act).append(").\n");

				} else if (DeclareParser.isDataBinding(line)) {
					String act = line.substring("bind ".length(), line.indexOf(':'));
					String[] boundAttributes = line.substring(line.indexOf(':') + 1).trim().split(",\\s+");

					for (String att : boundAttributes)
						lpBuilder.append("has_attribute(" + act + "," + att + ").\n");

				} else if (DeclareParser.isData(line)) {
					String att = line.substring(0, line.indexOf(':'));
					String valuesStr = line.substring(line.indexOf(':') + 1).trim();

					if (valuesStr.startsWith("integer between") || valuesStr.startsWith("float between")) {
						String[] tmp = valuesStr.split("\\s+");
						String lowerBound, upperBound;
						if (tmp[0].equals("integer")) {
							lowerBound = tmp[2];
							upperBound = tmp[4];
						} else { // Scaling floating attribute bounds to the lowest integers
							int digitsToScale = floatingAttributes.get(att);
							lowerBound = String.valueOf(Math.round(Double.valueOf(tmp[2]) * Math.pow(10, digitsToScale)));
							upperBound = String.valueOf(Math.round(Double.valueOf(tmp[4]) * Math.pow(10, digitsToScale)));
						}

						lpBuilder.append("value(" + att + "," + lowerBound + ".." + upperBound + ").\n");

					} else {
						String[] catValues = valuesStr.split(",\\s+");
						for (String val : catValues)
							lpBuilder.append("value(" + att + "," + val + ").\n");
					}

				} else if (DeclareParser.isDataConstraint(line)) {
					String lineWithoutTimeConds = line.substring(0, line.lastIndexOf('|')).trim();
					Matcher mConstraint = constraintPattern.matcher(lineWithoutTimeConds);

					if (mConstraint.find()) {
						String templateName = mConstraint.group(1);
						lpBuilder.append("template(" + constraintIndex + ",\"" + templateName + "\").\n");
						boolean isBinary = ConstraintTemplate.getByTemplateName(templateName).getIsBinary();

						String dataConditionsStr = mConstraint.group(3).replaceAll("(A\\.)|(T\\.)", "");
						Predicate[] dataConditions = new Predicate[isBinary ? 2 : 1];

						String firstCond;
						if (!isBinary)
							firstCond = dataConditionsStr.trim().substring(1).trim();
						else
							firstCond = dataConditionsStr.trim().substring(1, dataConditionsStr.lastIndexOf('|')).trim();

						if (firstCond.isBlank())
							dataConditions[0] = new LogicalPredicate(null, LogicalOperator.AND);
						else
							dataConditions[0] = Predicate.getPredicateFromString(firstCond);

						String[] activities = mConstraint.group(2).split(",\\s+");
						String activation = activities[0];
						Predicate actCond = dataConditions[0];

						if (isBinary) {
							String secondCond = dataConditionsStr.trim().substring(dataConditionsStr.lastIndexOf('|') + 1).trim();

							if (secondCond.isBlank())
								dataConditions[1] = new LogicalPredicate(null, LogicalOperator.AND);
							else
								dataConditions[1] = Predicate.getPredicateFromString(secondCond);

							String target;
							Predicate trgCond;
							if (ConstraintTemplate.getByTemplateName(templateName).getReverseActivationTarget()) {
								activation = activities[1];
								actCond = dataConditions[1];
								target = activities[0];
								trgCond = dataConditions[0];
							} else {
								target = activities[1];
								trgCond = dataConditions[1];
							}

							lpBuilder.append("target(" + constraintIndex + "," + target + ").\n");

							for (String e : getLPConditionsFromRootPredicate(trgCond, constraintIndex, "target"))
								lpBuilder.append(e + "\n");
						}

						lpBuilder.append("activation(" + constraintIndex + "," + activation + ").\n");

						for (String e : getLPConditionsFromRootPredicate(actCond, constraintIndex, "activation"))
							lpBuilder.append(e + "\n");

						constraintIndex++;
					}
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		return lpBuilder.toString();
	}


	private static List<String> getLPConditionsFromRootPredicate(Predicate root, int constraintIndex, String mode) throws OperationNotSupportedException {
		List<String> conditions = new ArrayList<>();
		List<String> childLeftParts = new ArrayList<>();

		String prefix = mode.equals("activation") ? "activation" : "correlation";
		String leftPartCondition = prefix + "_condition(" + constraintIndex + ",T)";

		if (root instanceof AttributePredicate) {
			getLPConditionFromSimplePredicate(conditions, (AttributePredicate) root, constraintIndex, mode);
			String childCondition = conditions.get(conditions.size() - 1);
			conditions.add(leftPartCondition + " :- " + childCondition.substring(0, childCondition.indexOf(':')).trim() + ".");

		} else {
			if (!root.getChildren().isEmpty()) {
				for (Predicate child : root.getChildren()) {
					if (child instanceof AttributePredicate)
						getLPConditionFromSimplePredicate(conditions, (AttributePredicate) child, constraintIndex, mode);
					else
						getLPConditionFromNestedPredicate(conditions, (LogicalPredicate) child, constraintIndex, mode);

					String childCondition = conditions.get(conditions.size() - 1);
					childLeftParts.add(childCondition.substring(0, childCondition.indexOf(':')).trim());
				}

				switch ((LogicalOperator) root.getOperator()) {
					case AND:
						conditions.add(leftPartCondition + " :- " + String.join(",", childLeftParts) + ".");
						break;
					case OR:
						for (String c : childLeftParts)
							conditions.add(leftPartCondition + " :- " + c + ".");
						break;
					default:
						throw new OperationNotSupportedException(
										"Operator: " + root.getOperator() + " is not yet supported!"
						);
				}
			}
		}

		if (conditions.isEmpty())  // When there are no related conditions, LP format needs the time(T) predicate to be added
			conditions.add(leftPartCondition + " :- time(T).");

		return conditions;
	}


	private static void getLPConditionFromNestedPredicate(List<String> conditions, LogicalPredicate nested, int constraintIndex, String mode) throws OperationNotSupportedException {

		List<String> childLeftParts = new ArrayList<>();
		for (Predicate child : nested.getChildren()) {
			if (child instanceof AttributePredicate)
				getLPConditionFromSimplePredicate(conditions, (AttributePredicate) child, constraintIndex, mode);
			else
				getLPConditionFromNestedPredicate(conditions, (LogicalPredicate) child, constraintIndex, mode);

			String childCondition = conditions.get(conditions.size() - 1);
			childLeftParts.add(childCondition.substring(0, childCondition.indexOf(':')).trim());
		}

		String prefix = mode.equals("activation") ? "act" : "corr";
		String leftPart = prefix + "_p" + conditions.size() + "(" + constraintIndex + ",T)";
		switch ((LogicalOperator) nested.getOperator()) {
			case AND:
				conditions.add(leftPart + " :- " + String.join(",", childLeftParts) + ".");
				break;
			case OR:
				for (String c : childLeftParts)
					conditions.add(leftPart + " :- " + c + ".");
				break;
			default:
				throw new OperationNotSupportedException(
								"Operator: " + nested.getOperator() + " is not yet supported!"
				);
		}
	}

	private static void getLPConditionFromSimplePredicate(List<String> conditions, AttributePredicate attrPred, int constraintIndex, String mode) throws OperationNotSupportedException {
		String prefix = mode.equals("activation") ? "act" : "corr";
		String leftPart = prefix + "_p" + conditions.size() + "(" + constraintIndex + ",T)";

		List<String> rightParts = new ArrayList<>();

		AttributeOperator op = (AttributeOperator) attrPred.getOperator();
		switch (op) {
			case EQ:
			case NEQ:
			case GEQ:
			case GT:
			case LEQ:
			case LT:
				boolean isFloatAtt = floatingAttributes.keySet().contains(attrPred.getAttribute());
				String value;
				if (isFloatAtt) { // Need to scale floatings because ASP works only with integers
					int scaleNum = floatingAttributes.get(attrPred.getAttribute());
					double doubleValue = Double.valueOf(attrPred.getValue()) * Math.pow(10, scaleNum);

					value = String.valueOf(Math.round(doubleValue));

				} else {
					value = attrPred.getValue();
				}

				rightParts.add("assigned_value(" + attrPred.getAttribute() + ",V,T),V" + attrPred.getOperator().getStringDisplay() + value + ".");
				break;

			case IN:
				for (String val : attrPred.getValue().substring(1, attrPred.getValue().length()).split(",\\s+"))
					rightParts.add("assigned_value(" + attrPred.getAttribute() + "," + val + ",T).");
				break;

			case NOT_IN:
				List<String> values = new ArrayList<>();
				for (String val : attrPred.getValue().substring(1, attrPred.getValue().length()).split(",\\s+"))
					values.add("assigned_value(" + attrPred.getAttribute() + "," + val + ",T)");

				rightParts.add(String.join(",", values) + ".");
				break;

			case IS:
				rightParts.add("assigned_value(" + attrPred.getAttribute() + "," + attrPred.getValue() + ",T).");
				break;

			case IS_NOT:
				rightParts.add("not assigned_value(" + attrPred.getAttribute() + "," + attrPred.getValue() + ",T).");
				break;

			default:
				throw new OperationNotSupportedException(
								"Operator: " + attrPred.getOperator() + " is not yet supported!"
				);
		}

		for (String rightPart : rightParts)
			conditions.add(leftPart + " :- " + rightPart);
	}


}
