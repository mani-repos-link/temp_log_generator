package toLP.Operators;

public enum AttributeOperator implements Operator {
	// Operators for numerical attributes
	GT(">", "&#62;", "&#62;"),
	GEQ(">=", "&#8805;", "&#8805;"),
	LT("<", "&#60;", "&#60;"),
	LEQ("<=", "&#8804;", "&#8804;"),
	EQ("=", "&#61;", "&#61;"),
	NEQ("!=", "&#8800;", "&#8800;"),

	// Operators for categorical attributes
	IS("is", "is", "<em>is</em>"),
	IS_NOT("is not", "is not", "<em>is not</em>"),
	IN("in", "in", "<em>in</em>"),				//TODO: (in, not in, same, different, exist) are never used!
	NOT_IN("not in", "not in", "<em>not in</em>"),

	// Operators for both types of attributes
	SAME("same", "same", "<em>same</em>"),
	DIFFERENT("different", "different", "<em>different</em>"),
	EXIST("exist", "exist", "<em>exist</em>");

	private String stringDisplay;
	private String declareDisplay;	// In order to achieve nicer representation in declare discovery view
	private String textualDisplay;	// In order to achieve nicer representation in textual discovery view

	private AttributeOperator(String stringDisplay, String declareDisplay, String textualDisplay) {
		this.stringDisplay = stringDisplay;
		this.declareDisplay = declareDisplay;
		this.textualDisplay = textualDisplay;
	}

	@Override
	public String getStringDisplay() {
		return this.stringDisplay;
	}

	@Override
	public String getDeclareDisplay() {
		return this.declareDisplay;
	}

	@Override
	public String getTextualDisplay() {
		return this.textualDisplay;
	}
}
