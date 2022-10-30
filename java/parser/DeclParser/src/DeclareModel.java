import lang.Activity;
import lang.Constraint;
import lang.DataConstraint;
import lang.data.EnumeratedData;
import lang.data.FloatData;
import lang.data.IntegerData;
import lang.trace.EnumTraceAttribute;
import lang.trace.FloatTraceAttribute;
import lang.trace.IntTraceAttribute;

import java.util.*;

/**
 * Created by Vasiliy on 2018-03-24.
 */
public class DeclareModel {

    private Set<Activity> activities;
    private Set<EnumeratedData> enumeratedData;
    private Set<IntegerData> integerData;
    private Set<FloatData> floatData;
    private List<Constraint> constraints;
    private List<DataConstraint> dataConstraints;

    private Map<String, Set<String>> activityToData;
    private Map<String, Set<String>> dataToActivity;

    private List<EnumTraceAttribute> enumTraceAttributes;
    private List<IntTraceAttribute> intTraceAttributes;
    private List<FloatTraceAttribute> floatTraceAttributes;

    public DeclareModel() {
        this.activities = new HashSet<>();
        this.enumeratedData = new HashSet<>();
        this.constraints = new ArrayList<>();
        this.dataConstraints = new ArrayList<>();

        this.activityToData = new HashMap<>();
        this.dataToActivity = new HashMap<>();

        this.integerData = new HashSet<>();
        this.floatData = new HashSet<>();
        this.enumeratedData = new HashSet<>();

        this.intTraceAttributes = new ArrayList<>();
        this.floatTraceAttributes = new ArrayList<>();
        this.enumTraceAttributes = new ArrayList<>();
    }
    
    // Copy constructor
    public DeclareModel(DeclareModel model) {
    	this.activities = new HashSet<>(model.getActivities());
    	this.enumeratedData = new HashSet<>(model.getEnumeratedData());
    	this.constraints = new ArrayList<>(model.getConstraints());
    	this.dataConstraints = new ArrayList<>(model.getDataConstraints());

        this.activityToData = new HashMap<>(model.getActivityToData());
        this.dataToActivity = new HashMap<>(model.getDataToActivity());

        this.integerData = new HashSet<>(model.getIntegerData());
        this.floatData = new HashSet<>(model.getFloatData());
        this.enumeratedData = new HashSet<>(model.getEnumeratedData());

        this.intTraceAttributes = new ArrayList<>(model.getIntTraceAttributes());
        this.floatTraceAttributes = new ArrayList<>(model.getFloatTraceAttributes());
        this.enumTraceAttributes = new ArrayList<>(model.getEnumTraceAttributes());
    }
    
    public Set<Activity> getActivities() {
        return activities;
    }

    public Set<EnumeratedData> getEnumeratedData() {
        return enumeratedData;
    }

    public void setEnumeratedData(Set<EnumeratedData> enumeratedData) {
        this.enumeratedData = enumeratedData;
    }

    public Set<IntegerData> getIntegerData() {
        return integerData;
    }

    public void setIntegerData(Set<IntegerData> integerData) {
        this.integerData = integerData;
    }

    public Set<FloatData> getFloatData() {
        return floatData;
    }

    public void setFloatData(Set<FloatData> floatData) {
        this.floatData = floatData;
    }

    public List<Constraint> getConstraints() {
        return constraints;
    }

    public void setConstraints(List<Constraint> constraints) {
        this.constraints = constraints;
    }

    public List<DataConstraint> getDataConstraints() {
        return dataConstraints;
    }

    public void setDataConstraints(List<DataConstraint> dataConstraints) {
        this.dataConstraints = dataConstraints;
    }

    public Map<String, Set<String>> getActivityToData() {
        return activityToData;
    }

    public void setActivityToData(Map<String, Set<String>> activityToData) {
        this.activityToData = activityToData;
    }

    public Map<String, Set<String>> getDataToActivity() {
        return dataToActivity;
    }

    public void setDataToActivity(Map<String, Set<String>> dataToActivity) {
        this.dataToActivity = dataToActivity;
    }

    public void setActivities(Set<Activity> activities) {
        this.activities = activities;
    }

    public List<EnumTraceAttribute> getEnumTraceAttributes() {
        return enumTraceAttributes;
    }

    public void setEnumTraceAttributes(List<EnumTraceAttribute> enumTraceAttributes) {
        this.enumTraceAttributes = enumTraceAttributes;
    }

    public List<IntTraceAttribute> getIntTraceAttributes() {
        return intTraceAttributes;
    }

    public void setIntTraceAttributes(List<IntTraceAttribute> intTraceAttributes) {
        this.intTraceAttributes = intTraceAttributes;
    }

    public List<FloatTraceAttribute> getFloatTraceAttributes() {
        return floatTraceAttributes;
    }

    public void setFloatTraceAttributes(List<FloatTraceAttribute> floatTraceAttributes) {
        this.floatTraceAttributes = floatTraceAttributes;
    }
    
    @Override
    public String toString() {
        return "DeclareModel{" +
                "activities=" + activities +
                ", constraints=" + constraints +
                ", dataConstraints=" + dataConstraints +
                '}';
    }
}
