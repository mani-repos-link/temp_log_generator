package lang;

import java.util.List;

/**
 * Created by Vasiliy on 2017-10-17.
 */
public class Constraint {
    String name;
    List<String> args;
    Statement statement;
    State state;
    
    public enum State {
        POSSIBLY_SATISFIED,
        POSSIBLY_VIOLATED,
        PERMANENTLY_VIOLATED,
        PERMANENTLY_SATISFIED,
        STATE_CONFLICT
    }
    
    public Constraint(String name, List<String> args, Statement statement) {
        this.name = name;
        this.args = args;
        this.statement = statement;
    }

    public String getName() {
        return name;
    }

    public List<String> getArgs() {
        return args;
    }

    public String taskA() {
        return args.get(0);
    }

    public String taskB() {
        return args.get(1);
    }

    public Statement getStatement() {
        return statement;
    }
    
    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public boolean isBinary() {
        return args.size() == 2;
    }

    public boolean supportsVacuity() {
        return isBinary() 
        		&& (
                    getName().equals("RespondedExistence") || getName().equals("CoExistence")
        				|| getName().equals("Response") || getName().equals("AlternateResponse") || getName().equals("ChainResponse")
        				|| getName().equals("Precedence") || getName().equals("AlternatePrecedence") || getName().equals("ChainPrecedence") 
        				|| getName().equals("Succession") || getName().equals("AlternateSuccession") || getName().equals("ChainSuccession")
        				|| getName().equals("NotRespondedExistence") || getName().equals("NotResponse") || getName().equals("NotPrecedence")
        				|| getName().equals("NotChainResponse") || getName().equals("NotChainPrecedence"));
    }
    
    @Override
    public String toString() {
        return "Constraint{name='" + name + "', args=" + args + ", statement=" + statement + "}";
    }
}
