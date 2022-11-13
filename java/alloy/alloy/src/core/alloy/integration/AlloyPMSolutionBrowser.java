package core.alloy.integration;


import core.Global;
import core.exceptions.BadSolutionException;
import core.models.declare.data.NumericToken;
import core.models.serialization.EventAdapter;
import core.models.serialization.Payload;
import edu.mit.csail.sdg.alloy4.Err;
import edu.mit.csail.sdg.alloy4compiler.ast.Expr;
import edu.mit.csail.sdg.alloy4compiler.ast.Module;
import edu.mit.csail.sdg.alloy4compiler.ast.Sig;
import edu.mit.csail.sdg.alloy4compiler.ast.Sig.PrimSig;
import edu.mit.csail.sdg.alloy4compiler.translator.A4Solution;
import edu.mit.csail.sdg.alloy4compiler.translator.A4Tuple;
import edu.mit.csail.sdg.alloy4compiler.translator.A4TupleSet;
import edu.mit.csail.sdg.alloy4whole.Helper;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class AlloyPMSolutionBrowser {
    private static Logger logger = Logger.getLogger("AlloySolutionBrowser");
    private Module module;
    private A4Solution solution;
    private Map<String, PrimSig> atomToSig;
    private int length;

    public AlloyPMSolutionBrowser(A4Solution solution, Module module, int length) {
        this.solution = solution;
        this.module = module;
        this.length = length;

        try {
            this.atomToSig = Helper.atom2sig(solution);
        } catch (Err err) {
            logger.log(Level.SEVERE, err.getMessage());
        }
    }

    public PrimSig atom2Sig(String atom) {
        return atomToSig.get(atom);
    }

    List<EventAdapter> orderedPEvents = null;

    public List<EventAdapter> orderPEvents() throws Err, IOException, BadSolutionException {
        if (orderedPEvents != null)
            return orderedPEvents;

        orderedPEvents = new ArrayList<>(length);
        for (int i = 0; i < length; ++i) {
            Expr taskExpr = exprFromString("TE" + i + ".task");
            String name = retrieveAtomLabel(taskExpr);
            if (name == null || name.equals("this/DummyActivity") && !Global.dummyActivitiesAllowed)  // end of trace with length<max
                continue;

            List<Payload> payload = retrievePayload(i);
            orderedPEvents.add(new EventAdapter(i, name, payload));
        }

        return orderedPEvents;
    }

    private List<Payload> retrievePayload(int pos) throws Err, IOException, BadSolutionException {
        Expr payloadExpr = exprFromString("TE" + pos + ".data");
        ArrayList<Payload> result = new ArrayList<>();
        for (A4Tuple t : ((A4TupleSet) solution.eval(payloadExpr))) {
            String name = getParentSignature(t.atom(0)).label;
            String value = atom2Sig(t.atom(0)).label;
            result.add(new Payload(name, value, getTokensFor(pos, name)));
        }

        if (result.stream().map(i -> i.getName()).distinct().count() != result.size())
            throw new BadSolutionException("Two payloads with the same name present in activity. Check alloy model. \n" +
                    String.join(", ", result.stream().map(i -> i.getName()).collect(Collectors.toList())));

        return result;
    }

    private Set<NumericToken> getTokensFor(int pos, String type) throws Err, IOException {
        Expr expr = exprFromString("(TE" + pos + ".tokens)");
        Set<NumericToken> tokens = new HashSet<>();
        for (A4Tuple t : (A4TupleSet) solution.eval(expr)) {
            String label = atom2Sig(t.atom(0)).label;
            if (label.substring(5 + Global.samePrefix.length()).startsWith(type.substring(5))) {  // 5 -- "this/".length
                NumericToken.Type ttype = getNumericTokenType(label);
                tokens.add(new NumericToken(ttype, label));
            }
        }

        return tokens;
    }

    public NumericToken.Type getNumericTokenType(String val) {
        NumericToken.Type ttype = null;
        if (val.startsWith("this/" + Global.samePrefix))
            ttype = NumericToken.Type.Same;
        else if (val.startsWith("this/" + Global.differentPrefix))
            ttype = NumericToken.Type.Different;

        return ttype;
    }

    private Sig getParentSignature(String atom) {
        return atom2Sig(atom).parent;
    }

    private String retrieveAtomLabel(Expr exprToTupleSet) throws Err {
        for (A4Tuple t : (A4TupleSet) solution.eval(exprToTupleSet)) {
            return atom2Sig(t.atom(0)).label;
        }

        return null;
    }

    public Expr exprFromString(String stringExpr) throws IOException, Err {
        return module.parseOneExpressionFromString(stringExpr);
    }
}
