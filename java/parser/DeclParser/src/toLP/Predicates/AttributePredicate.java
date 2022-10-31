package toLP.Predicates;


import toLP.Operators.AttributeOperator;
import toLP.Operators.Operator;
import toLP.Parsers;

import java.text.DecimalFormat;
import java.util.Set;

public class AttributePredicate extends Predicate {
	private String attribute;
	private String value;

	public AttributePredicate(Predicate parent, String attribute, AttributeOperator operator, String value) {
		super(parent, operator);
		this.attribute = attribute;

		if (Parsers.tryParseDouble(value)) {
			double doubleVal = Double.parseDouble(value);
			this.value = new DecimalFormat("#.####").format(doubleVal);	// Rounded to at most four decimal units

		} else {
			this.value = value;
		}
	}

	public String getAttribute() {
		return attribute;
	}

	public String getValue() {
		return value;
	}

	public int getPredicateSize() {
		return 1;
	}

	@Override
	public boolean isEmpty() {
		return false;
	}

	@Override
	public Predicate addChildren(Set<Predicate> children) {
		return this;
	}

	@Override
	public Set<Predicate> getChildren() {
		return null;
	}

	@Override
	public Predicate makeOpposite() {
		if (value.equals("true"))
			return new AttributePredicate(null, attribute, AttributeOperator.IS, "false");

		else if (value.equals("false"))
			return new AttributePredicate(null, attribute, AttributeOperator.IS, "true");

		else
			return new AttributePredicate(null, attribute, (AttributeOperator) Operator.getOpposite(this.getOperator()), value);
	}

	@Override
	public int getSize() {
		return 1;
	}

	@Override
	public String toString() {
		return this.attribute + " " + this.getOperator().getStringDisplay() + " " + this.value;
	}

	@Override
	public String toDeclareString() {
		return this.attribute + " " + this.getOperator().getDeclareDisplay() + " " + this.value;
	}

	@Override
	public String toTextualString() {
		return this.attribute + " " + this.getOperator().getTextualDisplay() + " " + this.value;
	}

	@Override
	public String toDotString(String id) {
		return id + "." + this.attribute + " " + this.getOperator().getStringDisplay() + " " + this.value;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((attribute == null) ? 0 : attribute.hashCode());
		result = prime * result + ((value == null) ? 0 : value.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		AttributePredicate other = (AttributePredicate) obj;
		if (attribute == null) {
			if (other.attribute != null)
				return false;
		} else if (!attribute.equals(other.attribute))
			return false;
		if (value == null) {
			if (other.value != null)
				return false;
		} else if (!value.equals(other.value))
			return false;
		return true;
	}
}

