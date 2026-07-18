export const DEFAULT_JAVA_FUNCTION_TEMPLATE = `import com.ispf.core.function.ObjectJavaFunction;
import com.ispf.core.function.JavaFunctionContext;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import java.util.Map;

public class MyObjectFunction implements ObjectJavaFunction {
    @Override
    public DataRecord invoke(DataRecord input, JavaFunctionContext context) {
        Object value = input != null && input.rowCount() > 0 ? input.firstRow().get("value") : null;
        DataSchema schema = DataSchema.builder("out").field("value", FieldType.STRING).build();
        return DataRecord.single(schema, Map.of("value", value == null ? "" : String.valueOf(value)));
    }
}
`;
