import fnparser.DeclareParserException;

public class Run {

	public static void main(String[] args) throws DeclareParserException {
		String decl = "activity A\n" +
						"bind A: grade\n" +
						"activity B\n" +
						"bind B: grade\n" +
						"bind B: mark\n" +
						"grade, mark: integer between 1 and 5\n" +
						"Response[A, B] |A.grade = 3 |B.grade = 5 |1,5,s";
		DeclareModel dm = DeclareParser.parse(decl);
		System.out.println("Test");
		System.out.println(dm);
		System.out.println(dm.getDataToActivity());
//		System.out.println(dm.getDataConstraints().get(0).getFirstFunction().getArgs());
//		System.out.println(dm.getDataConstraints().get(0).getFirstFunction().getExpression());
	}

}