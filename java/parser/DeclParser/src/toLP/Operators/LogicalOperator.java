package toLP.Operators;


public enum LogicalOperator implements Operator {
	AND("AND", "&#8743;", "&#8743;"),
	OR("OR", "&#8744;", "&#8744;");

	private String stringDisplay;
	private String declareDisplay;		// In order to achieve nicer representation in declare discovery view
	private String textualDisplay;	// In order to achieve nicer representation in textual discovery view

	private LogicalOperator(String stringDisplay, String declareDisplay, String textualDisplay) {
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

