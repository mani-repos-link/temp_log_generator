package validators;

//import com.google.gson.Gson;

import fnparser.DataExpressionParser;
import fnparser.DeclareParserException;

/**
 * Created by Vasiliy on 2018-06-09.
 */
public class FunctionValidator {

    public static String validate(String functionString) {
        ValidationResult result = new ValidationResult();
        try {
        	DataExpressionParser.parse(functionString);
        } catch (DeclareParserException e) {
            result.setErrorCode(1);
            result.setMessage(e.getMessage());
        }
        System.out.println("result: ");
        System.out.println(result);
//        return new Gson().toJson(result);
        return "";
    }

    public static class ValidationResult {
        int errorCode;
        String message;

        public int getErrorCode() {
            return errorCode;
        }

        public void setErrorCode(int errorCode) {
            this.errorCode = errorCode;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
