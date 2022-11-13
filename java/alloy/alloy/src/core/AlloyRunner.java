package core;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.deckfour.xes.extension.XExtensionParser;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.impl.XAttributeLiteralImpl;
import org.deckfour.xes.out.XesXmlSerializer;

import core.exceptions.BadSolutionException;
import core.exceptions.GenerationException;
import core.helpers.IOHelper;
import core.helpers.StatisticsHelper;
import core.models.AlloyRunConfiguration;
import core.models.AlloyRunConfiguration.ExecutionMode;
import declare.DeclareParserException;
import declare.validators.FunctionValidator;
import edu.mit.csail.sdg.alloy4.Err;

public class AlloyRunner {
	
	private static String GetDeclare(String file) {
        return IOHelper.readAllText(file);
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
	
	private static void writeTracesAsLogFile(AlloyRunConfiguration config, XLog plog) throws IOException {
        for (int i = 0; i < plog.size(); ++i)
            plog.get(i).getAttributes().put("concept:name", new XAttributeLiteralImpl("concept:name", "Case No. " + (i + 1)));

        addExtensions(plog);

        FileOutputStream fileOS = new FileOutputStream(config.logFilename);
        new XesXmlSerializer().serialize(plog, fileOS);
        fileOS.close();

        //StatisticsHelper.print();
        //StatisticsHelper.printTime();
    }
	
	public static boolean isValidDataExpression(String exp) {
		AlloyRunConfiguration config = new AlloyRunConfiguration();
		config.function = exp;
		config.mode = ExecutionMode.FUNCTION_VALIDATION;
		String res = FunctionValidator.validate(config.function);
		Pattern pattern = Pattern.compile("\\{\"errorCode\":(\\d)");
		Matcher m = pattern.matcher(res);
		return m.find() && m.group(1).equals("0");
	}
	
	public static void generateLog(String minTrace, String maxTrace, String traceNum,
			String declFile, String logFile, boolean isVac, boolean isNeg, boolean isEven) throws Exception {
		
		AlloyRunConfiguration config = new AlloyRunConfiguration();
		config.minLength = Integer.parseInt(minTrace);
        config.maxLength = Integer.parseInt(maxTrace);
        config.modelFilename = declFile;
        config.logFilename = logFile;
        config.alsFilename = "temp.als";
        config.mode = ExecutionMode.GENERATION;
        config.evenLengthsDistribution = isEven;
        config.shuffleStatementsIterations = 1;
        config.maxSameInstances = 2;
        config.intervalSplits = 1;
        config.underscore_spaces = true;
        Global.underscore_spaces = config.underscore_spaces;
        int n = Integer.parseInt(traceNum);
        if (isNeg)
            if (isVac)
                config.nNegativeVacuousTraces = n;
            else
                config.nNegativeTraces = n;
        else if (isVac)
            config.nVacuousTraces = n;
        else
            config.nPositiveTraces = n;
        
        String declare = GetDeclare(config.modelFilename);
        
        XLog plog = AssemblyGenerationModes.getLog(
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
	}

	/*public static void main(String[] args) throws Exception{
		// TODO Auto-generated method stub
		String baseDeclare = "/task definition\n" +
	            "activity ApplyForTrip\n" +
	            "activity ApproveApplication\n" +
	            "activity BookTransport\n" +
	            "activity BookAccomodation\n" +
	            "activity CollectTickets\n" +
	            "activity ArchiveDocuments\n" +
	            "activity UseTransport\n" +
	            "activity DoSomething\n" +
	            "\n" +
	            "/constraints\n" +
	            "Init[ApplyForTrip]\n" +
	            "Response[CollectTickets, ArchiveDocuments]\n" +
	            "Precedence[BookTransport, ApproveApplication]\n" +
	            "Precedence[BookAccomodation, ApproveApplication]\n" +
	            "Precedence[CollectTickets, BookTransport]\n" +
	            "Precedence[CollectTickets, BookAccomodation] \n" +
	            "Absence[BookAccomodation, 2]\n" +
	            "Absence[BookTransport, 3]\n" +
	            "/ChainResponse[UseTransport, DoSomething]\n" +
	            "/Existence[DoSomething]\n" +
	            "Absence[ApplyForTrip, 1]\n" +
	            "Existence[CollectTickets]\n" +
	            "Existence[ArchiveDocuments]\n" +
	            "Absence[ArchiveDocuments, 1]\n" +
	            "Absence[ApproveApplication, 1]\n" +
	            "\n" +
	            "/data definition\n" +
	            "TransportType: Car, Plane, Train, Bus\n" +
	            "Something: One, None, Another\n" +
	            "Price: float between 0 and 100\n" +
	            "Speed: integer between 0 and 300\n" +
	            "\n" +
	            "/data binding\n" +
	            "bind BookTransport: TransportType, Price, Speed\n" +
	            "bind UseTransport: TransportType, Something, Price\n" +
	            "bind DoSomething: Something\n";
		
		String declare = baseDeclare
                + "Init[ApplyForTrip A]|A.Something is One\n"
                + "bind ApplyForTrip: Something\n";

        XLog log = Evaluator.getLogSingleRun(
                5, 6,
                20,
                2,
                declare,
                "temp.als",
                2, false,
                false, false, LocalDateTime.now(),
                Duration.ofHours(4),
                null);
        FileOutputStream fileOS = new FileOutputStream("test.xes");
        new XesXmlSerializer().serialize(log, fileOS);
        fileOS.close();
	}*/

}

