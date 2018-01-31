package matcher.fernflower;

import matcher.type.ClassFeatureExtractor;
import matcher.type.ClassInstance;
import matcher.type.IClassEnv;
import org.jetbrains.java.decompiler.main.extern.IBytecodeProvider;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructContext;
import org.objectweb.asm.ClassWriter;

import java.io.IOException;
import java.io.InputStream;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by Thiakil on 7/01/2018.
 */
public class AsmContext implements IBytecodeProvider {

    public ClassFeatureExtractor classEnv;


    @Override
    public byte[] getBytecode(String externalPath, String internalPath) throws IOException {
        return new byte[0];
    }
}
