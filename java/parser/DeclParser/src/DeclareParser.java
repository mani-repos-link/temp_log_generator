import core.Global;
import fnparser.DataExpression;
import fnparser.DataExpressionParser;
import fnparser.DataFunction;
import lang.Activity;
import lang.Constraint;
import lang.DataConstraint;
import lang.Statement;
import lang.data.EnumeratedData;
import lang.data.FloatData;
import lang.data.IntegerData;
import lang.trace.EnumTraceAttribute;
import lang.trace.FloatTraceAttribute;
import lang.trace.IntTraceAttribute;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import fnparser.DeclareParserException;

/**
 * Created by Vasiliy on 2017-10-16.
 */
public final class DeclareParser {
	private static Pattern inRectBrackets = Pattern.compile(".*\\[\\s*(.+?)\\s*].*");

    private static List<String> tasksCode;
    private static List<String> traceAttributesCode;
    private static List<String> dataCode;
    private static List<String> dataBindingsCode;
    private static List<Statement> constraintsCode;
    private static List<Statement> dataConstraintsCode;

    private DeclareParser() {
    	throw new AssertionError();
    }

    public static DeclareModel parse(String declare) throws DeclareParserException {
        init();
        DeclareModel model = new DeclareModel();
        
        sortInput(splitStatements(declare));
        
        model.setActivities(parseActivities(tasksCode));
        checkInterference( declare, model.getActivities().stream().map(act -> act.getName()).collect(Collectors.toList()) );
        
        parseData(dataCode, model.getEnumeratedData(), model.getIntegerData(), model.getFloatData());
        for (EnumeratedData datum : model.getEnumeratedData())
        	checkInterference(declare, Stream.concat(datum.getValues().stream(), List.of(datum.getType()).stream()).collect(Collectors.toList()) );
        
        parseDataBindings(model.getActivityToData(), model.getDataToActivity());
        model.setConstraints(parseConstraints());
        model.setDataConstraints(parseDataConstraints(dataConstraintsCode));
        parseTraceAttributes(traceAttributesCode, model.getEnumTraceAttributes(), model.getIntTraceAttributes(), model.getFloatTraceAttributes());
        
        return model;
    }

    private static void init() {
        tasksCode = new ArrayList<>();
        traceAttributesCode = new ArrayList<>();
        dataCode = new ArrayList<>();
        dataBindingsCode = new ArrayList<>();
        constraintsCode = new ArrayList<>();
        dataConstraintsCode = new ArrayList<>();
    }

    private static void sortInput(String[] st) {
        int line = 0;
        for (String i : st) {
            ++line;

            if (i.isEmpty() || i.startsWith("/"))
                continue;

            if (isActivity(i))
                tasksCode.add(i);

            if (isTraceAttribute(i))
                traceAttributesCode.add(i);

            if (isData(i))
                dataCode.add(i);

            if (isDataBinding(i))
                dataBindingsCode.add(i);

            if (isConstraint(i))
                constraintsCode.add(new Statement(i, line));

            if (isDataConstraint(i))
                dataConstraintsCode.add(new Statement(i, line));
        }
    }

    private static void parseDataBindings(Map<String, Set<String>> activityToData, Map<String, Set<String>> dataToActivity) {
        for (String line : dataBindingsCode) {
            line = line.substring(5);
            List<String> data = Arrays.stream(line.split("[:,\\s+]+")).filter(i -> !i.isEmpty()).collect(Collectors.toList());
            
            String activity = data.get(0);
            if (!activityToData.containsKey(activity))
                activityToData.put(activity, new HashSet<>());
            
            for (String i : data.stream().skip(1).collect(Collectors.toList())) {
                activityToData.get(activity).add(i);
                if (!dataToActivity.containsKey(i))
                    dataToActivity.put(i, new HashSet<>());
                dataToActivity.get(i).add(activity);
            }
        }
    }

    private static List<Constraint> parseConstraints() {
        List<Constraint> constraints = new ArrayList<>();
        for (Statement s : constraintsCode) {
            String[] p = s.getCode().split("\\s*[\\[\\],]\\s*");
            constraints.add(new Constraint(p[0], Arrays.stream(p).skip(1).collect(Collectors.toList()), s));
        }

        return constraints;
    }

    public static boolean isActivity(String line) {
        return line.startsWith("activity ");
    }

    public static boolean isTraceAttribute(String line) {
        return line.startsWith("trace ");
    }

    public static boolean isData(String line) {
    	// regex for numeric data lines ".+:\\s+((integer|float)\\s+between\\s+-?\\d+(\\.\\d+)?\\s+and\\s+-?\\d+(\\.\\d+)?)"
    	return line.matches(".+:\\s+.+") && !isActivity(line) && !isTraceAttribute(line) && !isDataBinding(line);
    }

    public static boolean isDataBinding(String line) {
        return line.startsWith("bind ");
    }

    public static boolean isConstraint(String line) {
        return line.contains("[") && !isDataConstraint(line);
    }

    public static boolean isDataConstraint(String line) {
        return line.matches(".+\\[.+\\]\\s*(\\|[^\\|\\n\\r]*)+");	// ".+\\[.+\\]\\s*(\\|[^\\|\\n\\r]*){0,2}"
    }

    public static String[] splitStatements(String code) {
        return code.replace("\r\n", "\n").split("\n");
    }

    public static Set<Activity> parseActivities(List<String> tasksCode) {
        Set<Activity> data = new HashSet<>();
        for (String i : tasksCode) {
            String name = i.substring(9); // syntax: 'activity ActivityName'
            data.add(new Activity(name));
        }

        return data;
    }

    public static void parseData(List<String> dataCode, Set<EnumeratedData> edata, Set<IntegerData> idata, Set<FloatData> fdata) {
        for (String i : dataCode) {
            String[] a = i.split(":\\s*|,?\\s+");

            if (a[1].equals("integer") && a[2].equals("between"))
                idata.add(new IntegerData(a[0], Integer.parseInt(a[3]), Integer.parseInt(a[5]), true));
            else if (a[1].equals("float") && a[2].equals("between"))
                fdata.add(new FloatData(a[0], Float.parseFloat(a[3]), Float.parseFloat(a[5]), true));
            else
                edata.add(new EnumeratedData(a[0], Arrays.stream(a).skip(1).collect(Collectors.toList()), true));
        }
    }

    public static List<DataConstraint> parseDataConstraints(List<Statement> dataConstraintsCode) throws DeclareParserException {
        List<DataConstraint> dataConstraints = new ArrayList<>();
        for (Statement st : dataConstraintsCode) {
            String[] lr = st.getCode().split("\\|", -1);
            String activity = lr[0].substring(0, lr[0].indexOf('['));
            List<String[]> args = Arrays.stream(getActivityArgsFromConstraintText(lr[0]).split(",\\s*"))
					                    .map(i -> (i + " A").split("\\s+"))
					                    .collect(Collectors.toList());

            if (args.size() > 1)
                args.get(1)[args.get(1).length - 1] = "B";

            List<DataFunction> fns = new ArrayList<>();
            for (int i = 1; i < lr.length; ++i) {
                DataExpression expr = DataExpressionParser.parse(lr[i]);
                DataFunction fn = new DataFunction(args.stream().filter(x -> x.length >= 2).map(x -> x[1]).limit(i).collect(Collectors.toList()), expr);
                fns.add(fn);
            }

            DataConstraint c = new DataConstraint(activity, args.stream().map(i -> i[0]).collect(Collectors.toList()), fns, st);
            dataConstraints.add(c);
        }

        return dataConstraints;
    }


    private static String getActivityArgsFromConstraintText(String v) {
        Matcher m = inRectBrackets.matcher(v);
        m.matches();
        return m.group(1);
    }

    public static void parseTraceAttributes(List<String> traceAttributesCode, List<EnumTraceAttribute> eta,
                                     List<IntTraceAttribute> ita, List<FloatTraceAttribute> fta) {
        for (String i : traceAttributesCode) {
            String[] a = i.split(":\\s*|,?\\s+");

            if (a[2].equals("integer"))
                ita.add(new IntTraceAttribute(a[1], Integer.parseInt(a[4]), Integer.parseInt(a[6])));
            else if (a[2].equals("float"))
                fta.add(new FloatTraceAttribute(a[1], Float.parseFloat(a[4]), Float.parseFloat(a[6])));
            else
                eta.add(new EnumTraceAttribute(a[1], Arrays.stream(a).skip(2).collect(Collectors.toList())));
        }
    }
    
 // May throw exception if name might interfere with reserved keywords
    private static void checkInterference(String declare, List<String> names) {
        //String keywords = IOHelper.readAllText("./data/keywords.txt");
        String keywords = " activity x\n" +
                " Init[]\n" +
                " Existence[] \n" +
                " Existence[]\n" +
                " Absence[]\n" +
                " Absence[]\n" +
                " Exactly[]\n" +
                " Choice[] \n" +
                " ExclusiveChoice[] \n" +
                " RespondedExistence[] \n" +
                " Response[] \n" +
                " AlternateResponse[] \n" +
                " ChainResponse[]\n" +
                " Precedence[] \n" +
                " AlternatePrecedence[] \n" +
                " ChainPrecedence[] \n" +
                " NotRespondedExistence[] \n" +
                " NotResponse[] \n" +
                " NotPrecedence[] \n" +
                " NotChainResponse[]\n" +
                " NotChainPrecedence[]\n" +
                " integer between x and x\n" +
                " float between x and x\n" +
                " trace x\n" +
                " bind x\n" +
                " : , x\n" +
                " is not x\n" +
                " not in x\n" +
                " is x\n" +
                " in x\n" +
                " not x\n" +
                " or x\n" +
                " and x\n" +
                " same x\n" +
                " different x\n" +
                " not x\n" +
                " [ ] x\n" +
                " ( ) x\n" +
                " . x\n" +
                " > x\n" +
                " < x\n" +
                " >= x\n" +
                " <= x\n" +
                " = x\n" +
                " | x\n" +
                "\n";
        
        for (String name : names) {
	        if (Global.deepNamingCheck) {
	            Pattern pattern = Pattern.compile("[\\d\\w]" + name + "[\\d\\w]|[\\d\\w]" + name + "|" + name + "[\\d\\w]");
	            Matcher m = pattern.matcher(declare);
	            
	            if (m.find() && !name.contains(m.group(0)))
	                Global.log.accept("The name '" + name + "' might be part of reserved keyword. If other errors appear try to rename it or use in quote marks.");
	        
	        } else if (keywords.contains(name)) {
	            Global.log.accept("The name '" + name + "' might be part of reserved keyword. If other errors appear try to rename it or use in quote marks.");
	        }
        }
    }
}
