import Declare.DeclareModel;
import Declare.DeclareParser;
import fnparser.DeclareParserException;
import toLP.Decl2LP;

import java.util.ArrayList;
import java.util.List;

public class Run {

	public static void main(String[] args) throws Exception {
		List<String> lines = new ArrayList<>();
//		lines.add("Chain Response" +
//						"[ER Registration, ER Triage] |(A.DiagnosticArtAstrup is false) AND (A.SIRSCritHeartRate is true) AND" +
//						" (A.org:group is A) AND (A.DiagnosticBlood is true) AND (A.DisfuncOrg is false) AND (A.DiagnosticECG is true) AND" +
//						" (A.Age >= 45) AND (A.InfectionSuspected is true) AND (A.DiagnosticLacticAcid is true) AND (A.DiagnosticSputum is true) AND" +
//						" (A.Hypoxie is false) AND (A.DiagnosticUrinaryCulture is true) AND (A.DiagnosticLiquor is false) AND" +
//						" (A.SIRSCritTemperature is true) AND (A.Infusion is true) AND (A.Hypotensie is false) AND" +
//						" (A.DiagnosticUrinarySediment is true) AND (A.Oligurie is false) AND (A.Age <= 80) AND (A.SIRSCritTachypnea is true) AND" +
//						" (A.DiagnosticOther is false) AND (A.SIRSCritLeucos is false) AND (A.DiagnosticIC is true) AND" +
//						" (A.SIRSCriteria2OrMore is true) AND (A.DiagnosticXthorax is true) |T.org:group is C |52,2154,s");
//
//		lines.add("Chain Precedence[Admission IC, Admission NC] |A.org:group is J |T.org:group is J |");
//		lines.add("Response[A, B] |A.grade > 2 and A.name in (x, y) or A.grade < 3 and A.name in (z, v) |T.grade > 5 |1,5,s");
//		lines.add("Response[A, B] |A.grade > 2 and A.name is false |T.grade > 5 |1,5,s");
//		lines.add("Response[A, B] |A.grade < 2  | T.mark > 2|1,5,s");
		lines.add("Response[A, B] |A.grade < 2 or A.grade < 5 | T.mark > 2|1,5,s");
//		lines.add("Response[A, B] |A.grade is Char  | T.mark > 2|1,5,s");
//		lines.add("Response[A, B] |A.name in (marco, pippo, ale) | |");

		Decl2LP lp = new Decl2LP();
		for (String line : lines) {
			String s = lp.parseDataConstraint(line, new StringBuilder());
			System.out.println(s);
		}

//		String decl = "activity A\n" +
//						"bind A: grade\n" +
//						"activity B\n" +
//						"bind B: grade\n" +
//						"bind B: mark\n" +
//						"grade, mark: integer between 1 and 5\n" +
//						"Response[A, B] |A.grade = 3 |B.grade = 5 |1,5,s";
//		DeclareModel dm = DeclareParser.parse(decl);
////		System.out.println("Test");
////		System.out.println(dm);
////		System.out.println(dm.getDataToActivity());
//		String lp = Decl2LP.decl2lp(decl);
//		System.out.println(lp);
//		System.out.println(dm.getDataConstraints().get(0).getFirstFunction().getArgs());
//		System.out.println(dm.getDataConstraints().get(0).getFirstFunction().getExpression());


	}


}
