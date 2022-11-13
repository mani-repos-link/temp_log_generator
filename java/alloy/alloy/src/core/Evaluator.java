package src.core;

import core.alloy.codegen.AlloyCodeGenerator;
import core.alloy.codegen.NameEncoder;
import core.alloy.integration.AlloyComponent;
import core.alloy.integration.QueryExtractor;
import core.alloy.serialization.AlloyLogExtractor;
import core.exceptions.BadSolutionException;
import core.exceptions.GenerationException;
import core.helpers.IOHelper;
import core.helpers.StatisticsHelper;
import core.models.AlloyRunConfiguration;
import core.models.query.AggregationState;
import core.models.query.QueryEvent;
import core.models.query.QueryState;
import core.models.query.TraceQueryResults;
import core.models.serialization.trace.AbstractTraceAttribute;
import core.models.serialization.trace.EnumTraceAttributeImpl;
import core.models.serialization.trace.FloatTraceAttributeImpl;
import core.models.serialization.trace.IntTraceAttributeImpl;
import edu.mit.csail.sdg.alloy4.Err;
import edu.mit.csail.sdg.alloy4compiler.translator.A4Solution;
import org.apache.commons.lang3.tuple.Pair;
import org.deckfour.xes.extension.XExtensionParser;
import org.deckfour.xes.in.XesXmlParser;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.deckfour.xes.model.impl.XAttributeLiteralImpl;
import org.deckfour.xes.model.impl.XLogImpl;
import org.deckfour.xes.out.XesXmlSerializer;
import src.declare.DeclareModel;
import src.declare.DeclareParser;
import src.declare.DeclareParserException;
import src.declare.lang.Activity;
import src.declare.lang.Statement;
import src.declare.lang.trace.EnumTraceAttribute;
import src.declare.lang.trace.FloatTraceAttribute;
import src.declare.lang.trace.IntTraceAttribute;
import src.declare.validators.FunctionValidator;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static core.models.AlloyRunConfiguration.ExecutionMode;

public class Evaluator {

    private static int reuse = 1;

    private static final boolean testMode = true;

    private static AlloyRunConfiguration debugConf() {
        AlloyRunConfiguration conf = new AlloyRunConfiguration();
        conf.waitInputBeforeExit = false;
        conf.minLength = 100;
        conf.maxLength = 100;
        conf.nPositiveTraces = 10;
        //conf.nNegativeVacuousTraces = 10;
        conf.modelFilename = "../data/query.decl";
//        conf.modelFilename = "../data/loanApplication.decl";
        conf.shuffleStatementsIterations = 0;
        conf.evenLengthsDistribution = false;
        conf.intervalSplits = 1;
        conf.alsFilename = "../data/temp.als";
        conf.logFilename = "../data/2018-12-27-L10-15.xes";
//        conf.logFilename = "../data/" + LocalDate.now() + "-L" + conf.minLength + "-" + conf.maxLength + ".xes";
        conf.mode = ExecutionMode.QUERY;
        return conf;
    }

    public static void main(String[] args) throws Exception {
        AlloyRunConfiguration config = resolveConfig(args);
        if (config == null) return;

        if (config.mode == ExecutionMode.GENERATION) {
            try {
                long start = System.nanoTime();
                StatisticsHelper.time.add(start);
                String declare = GetDeclare(config.modelFilename);

                XLog plog = core.AssemblyGenerationModes.getLog(
                        config.minLength,
                        config.maxLength,
                        config.nPositiveTraces,
                        config.nVacuousTraces,
                        config.nNegativeTraces,
                        config.nNegativeVacuousTraces,
                        config.shuffleStatementsIterations,
                        config.evenLengthsDistribution,
                        config.maxSameInstances,
                        config.intervalSplits,
                        declare,
                        config.alsFilename,
                        LocalDateTime.now(),
                        Duration.ofHours(4),
                        Evaluator::getLogSingleRun);

                writeTracesAsLogFile(config, plog);

                long end = System.nanoTime();
                core.Global.log.accept("total time (ms): " + ((end - start) / 1_000_000));

            } catch (Throwable e) {
                e.printStackTrace();
            }

            if (config.waitInputBeforeExit) {
                core.Global.log.accept("press enter to close");
                System.in.read();
            }

        } else if (config.mode == ExecutionMode.FUNCTION_VALIDATION) {
            core.Global.log.accept(FunctionValidator.validate(config.function));
        } else if (config.mode == ExecutionMode.COMPLIANCE_CHECK) {
            XLog log = readTracesFromLogFile(config.logFilename);
            String declare = GetDeclare(config.modelFilename);

            List<Statement>[] results = new List[log.size()];
            int i = 0;
            for (XTrace trace : log) {
                results[i] = Evaluator.checkCompliance(
                        config.maxLength,
                        declare,
                        config.alsFilename,
                        false,
                        trace);
                ++i;
            }

            i = 0;
            core.Global.log.accept("\n------------------");
            for (List<Statement> violations : results) {
                ++i;
                if (violations.isEmpty()) {
                    core.Global.log.accept(i + ": ok");
                } else {
                    core.Global.log.accept(i + ": violated\n" + String.join("\n", violations.stream().map(x -> " at line " + x.getLine() + ": " + x.getCode()).collect(Collectors.toList())));
                }

                core.Global.log.accept("");
            }


        } else if (config.mode == ExecutionMode.QUERY) {
            XLog log = readTracesFromLogFile(config.logFilename);
            String declare = GetDeclare(config.modelFilename);

            List<TraceQueryResults> allStates = queryLog(declare, config.alsFilename, false, log);
            Map<QueryState, AggregationState> aggregatedData = aggregate(allStates, 0, 0);

            double traceCount = allStates.size();
            core.Global.log.accept("Found: " + aggregatedData.size());
            StringBuilder out = new StringBuilder();
            for (Map.Entry<QueryState, AggregationState> state : aggregatedData.entrySet()) {
                out.append("\n support: ").append(state.getValue().count / traceCount).append("; vacuous: ").append(state.getValue().vacuousCount / traceCount).append("\r\n");
                for (QueryEvent r : state.getKey().getTemplateValuesMap().values()) {
                    out.append(r.toString()).append("\r\n");
                }
            }

            core.Global.log.accept(out.toString());
            Files.write(Paths.get("out.txt"), out.toString().getBytes());
        } else {
            Global.log.accept("Unknown execution mode");
        }
    }

    private static Map<QueryState, AggregationState> aggregate(List<TraceQueryResults> allStates, double threshold, double vacuousThreshold) {
        Map<QueryState, AggregationState> a = new HashMap<>();
        for (TraceQueryResults traceState : allStates) {
            for (QueryState state : traceState.getStates()) {
                a.computeIfAbsent(state, i -> new AggregationState());
                AggregationState current = a.get(state);
                ++current.count;
                current.vacuousCount += state.getTemplateValuesMap().values().stream().anyMatch(QueryEvent::isVacuous) ? 1 : 0;
            }
        }

        double count = allStates.size();
        Set<QueryState> removeQ = new HashSet<>();
        for (Map.Entry<QueryState, AggregationState> entry : a.entrySet()) {
            if (entry.getValue().count / count < threshold || entry.getValue().vacuousCount / count < vacuousThreshold) {
                removeQ.add(entry.getKey());
            }
        }

        removeQ.forEach(a::remove);

        return a;
    }

    private static void writeTracesAsLogFile(Al0loyRunConfiguration config, XLog plog) throws IOException {
        for (int i = 0; i < plog.size(); ++i)
            plog.get(i).getAttributes().put("concept:name", new XAttributeLiteralImpl("concept:name", "Case No. " + (i + 1)));

        addExtensions(plog);

        Global.log.accept("Writing XES for: " + config.logFilename);
        Global.log.accept(plog.size() + "traces generated");
        FileOutputStream fileOS = new FileOutputStream(config.logFilename);
        new XesXmlSerializer().serialize(plog, fileOS);
        fileOS.close();

        StatisticsHelper.print();
        //StatisticsHelper.printTime();
        Global.log.accept("SUCCESS");
    }

    private static XLog readTracesFromLogFile(String filename) throws Exception {
        Global.log.accept("Reading XES from " + filename);
        FileInputStream fileIS = new FileInputStream(filename);
        List<XLog> log = new XesXmlParser().parse(fileIS);
        fileIS.close();
        return log.get(0);
    }

    private static AlloyRunConfiguration resolveConfig(String[] args) {
        AlloyRunConfiguration config;
        if (testMode)
            config = debugConf();
        else
            config = CLI.getConfigFromArgs(args);

        if (config == null)
            return null;

        Global.underscore_spaces = config.underscore_spaces;
        return config;
    }

    private static void addExtensions(XLog log) {
        if (Global.noExtensions)
            return;

        try {
            log.getExtensions().add(XExtensionParser.instance().parse(new URI("http://www.xes-standard.org/lifecycle.xesext")));
            log.getExtensions().add(XExtensionParser.instance().parse(new URI("http://www.xes-standard.org/org.xesext")));
            log.getExtensions().add(XExtensionParser.instance().parse(new URI("http://www.xes-standard.org/time.xesext")));
            log.getExtensions().add(XExtensionParser.instance().parse(new URI("http://www.xes-standard.org/concept.xesext")));
            log.getExtensions().add(XExtensionParser.instance().parse(new URI("http://www.xes-standard.org/semantic.xesext")));
            log.getGlobalTraceAttributes().add(new XAttributeLiteralImpl("concept:name", "__INVALID__"));
            log.getGlobalEventAttributes().add(new XAttributeLiteralImpl("concept:name", "__INVALID__"));
            log.getAttributes().put("source", new XAttributeLiteralImpl("source", "DAlloy"));
            log.getAttributes().put("concept:name", new XAttributeLiteralImpl("concept:name", "Artificial Log"));
            log.getAttributes().put("lifecycle:model", new XAttributeLiteralImpl("lifecycle:model", "standard"));
        } catch (Exception ex) {
            Global.log.accept("O-o-ops. Something happened, no log extensions will be written. Log itself is untouched");
            ex.printStackTrace();
        }
    }


    public static XLog getLogSingleRun(int minTraceLength,
                                       int maxTraceLength,
                                       int numberOfTraces,
                                       int maxSameInstances, // higher values of this parameter can have significant performance impact for some models. Keep it 1 unless you use same/different constraints for numbers. Otherwise recommended to increment by 1
                                       String declare,
                                       String alsFilename,
                                       int intervalSplits,  // minimum 1
                                       boolean vacuity,
                                       boolean negativeTraces,
                                       boolean shuffleConstraints,
                                       LocalDateTime start,
                                       Duration duration,
                                       XTrace trace)
            throws Err, IOException, DeclareParserException, BadSolutionException, GenerationException {

        if (numberOfTraces == 0)
            return new XLogImpl(null);

        Global.log.accept("Maximum no of traces: " + numberOfTraces);

        int bitwidth = 5;

        NameEncoder encoder = new NameEncoder();
        if (Global.encodeNames) {
        	encoder.createDeclMapping(declare);
            declare = encoder.encodeDeclModel(declare);
        }
        
        DeclareModel model = DeclareParser.parse(declare);
        AlloyCodeGenerator gen = new AlloyCodeGenerator(maxTraceLength, minTraceLength, bitwidth, maxSameInstances, vacuity, shuffleConstraints, true);
        gen.runLogGeneration(model, negativeTraces, intervalSplits, trace, "log_generation");

        String alloyCode = gen.getAlloyCode();
        IOHelper.writeAllText(alsFilename, alloyCode);

        AlloyComponent alloy = new AlloyComponent();
        Module world = alloy.parse(alsFilename);
        A4Solution solution = alloy.executeFromFile(maxTraceLength, bitwidth);

        Global.log.accept("Found Solution: " + (solution != null && solution.satisfiable()));

        AlloyLogExtractor ale = new AlloyLogExtractor(world, gen.generateNumericMap(), getTraceAttributesImpl(model),
                										encoder, start, duration);
        return ale.extract(solution, numberOfTraces, maxTraceLength, reuse);
    }

    // returns List of violated statements
    public static List<Statement> checkCompliance(int maxTraceLength,
                                                 String declare,
                                                 String alsFilename,
                                                 boolean vacuity,
                                                 XTrace trace)
            throws Err, IOException, DeclareParserException, GenerationException {

        int bitwidth = 5;
        DeclareModel model = DeclareParser.parse(declare);
        LogToModel logToModel = new LogToModel();
        Set<String> inferredActivities = logToModel.parseActivities(trace);
        model.getActivities().forEach(i -> inferredActivities.remove(i.getName()));
        inferredActivities.forEach(i -> model.getActivities().add(new Activity(i)));


        AlloyCodeGenerator gen = new AlloyCodeGenerator(maxTraceLength, 0, bitwidth, 1, vacuity, false, false);
        gen.runLogGeneration(model, false, 1, trace, "log_generation");

        String alloyCode = gen.getAlloyCode();
        IOHelper.writeAllText(alsFilename, alloyCode);

        AlloyComponent alloy = new AlloyComponent();
        Module world = alloy.parse(alsFilename);
        A4Solution solution = alloy.executeFromFile(maxTraceLength, bitwidth);

        List<Statement> violations = new ArrayList<>();
        if (solution != null && solution.satisfiable()) {
            for (Pair<Statement, String> constraint : gen.getAlloyConstraints()) {
                Object ok = solution.eval(world.parseOneExpressionFromString(constraint.getValue()));
                if (ok instanceof Boolean && !(Boolean) ok) {
                    violations.add(constraint.getKey());
                }
            }
        } else {
            Global.log.accept("Solution not found");
        }

        return violations;
    }

    public static List<TraceQueryResults> queryLog(String queryDeclare,
                                                   String alsFilename,
                                                   boolean vacuity,
                                                   XLog log)
            throws Err, DeclareParserException, GenerationException, IOException {

        LogToModel logToModel = new LogToModel();
        DeclareModel model = logToModel.parse(log);

        List<TraceQueryResults> allStates = new ArrayList<>();
        int n = 0;
        for (XTrace trace : log) {
            try {
                Global.log.accept("Trace " + ++n + " / " + log.size());
                TraceQueryResults traceStates = Evaluator.queryTrace(
                        queryDeclare,
                        alsFilename,
                        vacuity,
                        trace,
                        model,
                        logToModel.getActivityNameToCode());

                allStates.add(traceStates);
            } catch (Exception e) {
                Global.log.accept(e.getMessage());
                e.printStackTrace();
            }
        }

        allStates.stream().flatMap(i -> i.getStates().stream()).flatMap(i -> i.getTemplateValuesMap().values().stream())
                .forEach(i -> i.decode(logToModel.getCodeToName()));

        return allStates;
    }

    public static TraceQueryResults queryTrace(String queryDeclare,
                                               String alsFilename,
                                               boolean vacuity,
                                               XTrace trace,
                                               DeclareModel model,
                                               Map<String, String> nameToCode)
            throws Err, DeclareParserException, GenerationException, IOException {

        int bitwidth = 5;
        DeclareModel qModel = DeclareParser.parse(queryDeclare);
        if (!(qModel.getActivities().isEmpty() && qModel.getEnumeratedData().isEmpty() &&
                qModel.getIntegerData().isEmpty() && qModel.getFloatData().isEmpty())) {
            Global.log.accept("Warning: Activity and data definitions in query templates will be ignored");
        }

        QueryBuider qb = new QueryBuider(nameToCode);
        model.setConstraints(qModel.getConstraints());
        model.setDataConstraints(qModel.getDataConstraints());
        qb.extractQueryParams(model.getConstraints());
        qb.extractQueryParams(model.getDataConstraints());


        AlloyCodeGenerator gen = new AlloyCodeGenerator(trace.size(), 0, bitwidth, 0, vacuity, false, true);
        gen.runLogGeneration(model, false, 1, trace, "log_generation");
        gen.generateQueryPlaceholder(qb.getParamEncoding(), qb.getDataParams());
        gen.generateDataBindingForQuerying(model.getActivityToData(), model.getDataToActivity());

        String alloyCode = gen.getAlloyCode();
        IOHelper.writeAllText(alsFilename, alloyCode);

        AlloyComponent alloy = new AlloyComponent();
        Module world = alloy.parse(alsFilename);
        A4Solution solution = alloy.executeFromFile(trace.size(), bitwidth);

        QueryExtractor extractor = new QueryExtractor();
        Set<QueryState> qlist = extractor.get(solution, world, qb.getParamEncoding(), qb.getDataParams(), gen.getNumericData(), 1000);

        String name = ((XAttributeLiteralImpl) trace.getAttributes().get("concept:name")).getValue();
        return new TraceQueryResults(name, qlist);
    }

    private static List<AbstractTraceAttribute> getTraceAttributesImpl(DeclareModel model) {
        List<AbstractTraceAttribute> attributes = new ArrayList<>(model.getEnumTraceAttributes().size() + model.getIntTraceAttributes().size() + model.getFloatTraceAttributes().size());

        for (EnumTraceAttribute i : model.getEnumTraceAttributes()) {
            attributes.add(new EnumTraceAttributeImpl(i.getName(), i.getParams()));
        }

        for (IntTraceAttribute i : model.getIntTraceAttributes()) {
            attributes.add(new IntTraceAttributeImpl(i.getName(), i.getLow(), i.getHigh()));
        }

        for (FloatTraceAttribute i : model.getFloatTraceAttributes()) {
            attributes.add(new FloatTraceAttributeImpl(i.getName(), i.getLow(), i.getHigh()));
        }

        return attributes;
    }

    private static String GetDeclare(String file) {
        return IOHelper.readAllText(file);
    }
}
