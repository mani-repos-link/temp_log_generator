package core.monitoring;

import core.models.declare.data.NumericDataImpl;
import core.models.intervals.FloatInterval;
import core.models.intervals.IntegerInterval;
import declare.DeclareParserException;
import declare.DeclareModel;
import declare.lang.data.FloatData;
import declare.lang.data.IntegerData;

import org.deckfour.xes.extension.std.XConceptExtension;
import org.deckfour.xes.model.XAttribute;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XTrace;
import org.deckfour.xes.model.impl.XAttributeContinuousImpl;
import org.deckfour.xes.model.impl.XAttributeDiscreteImpl;
import org.deckfour.xes.model.impl.XAttributeLiteralImpl;

import java.util.Map;
import java.util.Optional;

public class TraceAlloyCode {

    StringBuilder traceCode = new StringBuilder();
    StringBuilder activitiesCode = new StringBuilder();
    Map<String, NumericDataImpl> numericData;

    public void run(XTrace trace, DeclareModel model, boolean isData) throws DeclareParserException {
        if (trace == null || trace.size() == 0) {
            return;
        }
        traceGenRun(trace, model, isData);
        attachTraceConstraints();
    }

    private void attachTraceConstraints(){
        traceCode.append("fact {\n").append(String.join("\n", activitiesCode)).append("\n}\n");
    }

    public String getTraceCode() {
        if (traceCode != null)
            return traceCode.toString();
        return null;
    }

    public void traceGenRun(XTrace t, DeclareModel model, boolean data) throws DeclareParserException {
        int index = 0;
        for (XEvent e : t){
            String name = XConceptExtension.instance().extractName(e);
            if (name == null || name ==  "") 
                throw new DeclareParserException("Event name not found in " + e);
            
            activitiesCode.append(name + " = TE" + index + ".task" + "\n");
            
            if (data) {
                for (XAttribute at : e.getAttributes().values()) {

                    if (at instanceof XAttributeLiteralImpl && model.getEnumeratedData().stream().anyMatch(i -> i.getType().equals(at.getKey()) && i.getValues().contains(((XAttributeLiteralImpl) at).getValue()))) {
                        activitiesCode.append(((XAttributeLiteralImpl) at).getValue()).append(" = TE").append(index).append(".data & ").append(at.getKey()).append("\n");
                    }

                    if (at instanceof XAttributeDiscreteImpl) {
                        Optional<IntegerData> intData = model.getIntegerData().stream().filter(i -> i.getType().equals(at.getKey())).findAny();
                        if (intData.isPresent()) {
                            activitiesCode.append(getIntervalFor(intData.get(), ((XAttributeDiscreteImpl) at).getValue())).append(" = TE").append(index).append(".data & ").append(at.getKey()).append("\n");
                        }
                    }

                    if (at instanceof XAttributeContinuousImpl) {
                        Optional<FloatData> floatData = model.getFloatData().stream().filter(i -> i.getType().equals(at.getKey())).findAny();
                        if (floatData.isPresent())
                            activitiesCode.append(getIntervalFor(floatData.get(), ((XAttributeContinuousImpl) at).getValue())).append(" = TE").append(index).append(".data & ").append(at.getKey()).append("\n");
                    }
                }
            }
            
            index++;
        }
    }

    public void setNumericData(Map<String, NumericDataImpl> numericData) {
        this.numericData = numericData;
    }

    private String getIntervalFor(IntegerData integerData, long attributeValue) throws DeclareParserException {
        NumericDataImpl numericData = this.numericData.get(integerData.getType());
        return numericData.getMapping().entrySet().stream().filter(i -> ((IntegerInterval) i.getValue()).isIn((int) attributeValue)).map(Map.Entry::getKey).findAny()
                .orElseThrow(() -> new DeclareParserException("no interval for " + integerData.getType() + " = " + attributeValue));
    }

    private String getIntervalFor(FloatData floatData, double attributeValue) throws DeclareParserException {
        NumericDataImpl numericData = this.numericData.get(floatData.getType());
        return numericData.getMapping().entrySet().stream().filter(i -> ((FloatInterval) i.getValue()).isIn((float) attributeValue)).map(Map.Entry::getKey).findAny()
                .orElseThrow(() -> new DeclareParserException("no interval for " + floatData.getType() + " = " + attributeValue));
    }
}
