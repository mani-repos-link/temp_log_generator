package toLP.Predicates;


import toLP.Operators.AttributeOperator;
import toLP.Operators.LogicalOperator;
import toLP.Operators.Operator;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class LogicalPredicate extends Predicate {
	private Set<Predicate> children;

	public LogicalPredicate(Predicate parent, LogicalOperator operator) {
		super(parent, operator);
		this.children = new HashSet<>();
	}

	@Override
	public Set<Predicate> getChildren() {
		return children;
	}

	private void addChild(Predicate pred) {
		if (pred.getChildren() != null) {

			if (pred.getChildren().size()==1 || this.getOperator().equals(pred.getOperator())) {

				this.addChildren(pred.getChildren());

			} /*else if (this.getChildren().size() == 1) {

				this.setOperator(pred.getOperator());
				this.addChildren(pred.getChildren());

			}*/ else {
				pred.setParent(this);
				children.add(pred);
			}

		} else {
			pred.setParent(this);
			children.add(pred);
		}
	}

	@Override
	public Predicate addChildren(Set<Predicate> children) {

		for (Predicate child : children)
			this.addChild(child);

		return this.checkConsistency();
	}

	private Predicate checkConsistency() {
		if (children.size() == 1) {
			Predicate child = this.children.iterator().next();
			child.setParent(null);

			if (child.getChildren() == null) {
				AttributePredicate attPr = (AttributePredicate) child;
				return new AttributePredicate(this.getParent(), attPr.getAttribute(), (AttributeOperator)attPr.getOperator(), attPr.getValue());

			} else {
				LogicalPredicate logPr = (LogicalPredicate) child;
				return new LogicalPredicate(this.getParent(), (LogicalOperator)logPr.getOperator()).addChildren(logPr.getChildren());
			}
		}

		return this;
	}

	@Override
	public boolean isEmpty() {
		return children.stream().allMatch(child -> child.isEmpty());
	}

	@Override
	public Predicate makeOpposite() {
		Set<Predicate> oppositeChildren = new HashSet<>();
		for (Predicate child : children)
			oppositeChildren.add(child.makeOpposite());

		LogicalOperator oppositeOperator = (LogicalOperator) Operator.getOpposite(this.getOperator());
		return new LogicalPredicate(null, oppositeOperator).addChildren(oppositeChildren);
	}

	@Override
	public int getSize() {
		int size = 0;

		for (Predicate child : children)
			size += child.getSize();

		return size;
	}

	@Override
	public String toString() {
		String output = "";
		Iterator<Predicate> it = children.iterator();

		if (it.hasNext())
			output += "(" + it.next().toString() + ")";

		while (it.hasNext())
			output += " " + this.getOperator().getStringDisplay() + " (" + it.next().toString() + ")";

		return output;
	}

	@Override
	public String toDeclareString() {
		String output = "";
		Iterator<Predicate> it = children.iterator();

		if (it.hasNext())
			output += "(" + it.next().toDeclareString() + ")";

		while (it.hasNext())
			output += " " + this.getOperator().getDeclareDisplay() + " (" + it.next().toDeclareString() + ")";

		return output;
	}

	@Override
	public String toTextualString() {
		String output = "";
		Iterator<Predicate> it = children.iterator();

		if (it.hasNext())
			output += "(" + it.next().toTextualString() + ")";

		while (it.hasNext())
			output += " " + this.getOperator().getTextualDisplay() + " (" + it.next().toTextualString() + ")";

		return output;
	}

	@Override
	public String toDotString(String id) {
		String output = "";
		Iterator<Predicate> it = children.iterator();

		if (it.hasNext())
			output += "(" + it.next().toDotString(id) + ")";

		while (it.hasNext())
			output += " " + this.getOperator().getStringDisplay() + " (" + it.next().toDotString(id) + ")";

		return output;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((children == null) ? 0 : children.hashCode());
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
		LogicalPredicate other = (LogicalPredicate) obj;
		if (children == null) {
			if (other.children != null)
				return false;
		} else if (!children.equals(other.children))
			return false;
		return true;
	}
}