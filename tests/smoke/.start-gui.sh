#!/usr/bin/env bash
cd /Users/lxd/Projects/ai/uml-project/argouml
CP="src/argouml-app/target/classes:src/argouml-ai/target/classes:src/argouml-ai/target/test-classes"
for jar in /tmp/argouml-deps/*.jar; do
    [ "$jar" = "/tmp/argouml-deps/argouml-0.35.2-SNAPSHOT.jar" ] && continue
    CP="$CP:$jar"
done
CP="$CP:/tmp/argouml-deps/argouml-0.35.2-SNAPSHOT.jar"
CP="$CP:src/argouml-build/target/argouml-jar-with-dependencies.jar"
ulimit -f 2097152 2>/dev/null || true
exec /usr/bin/java -Xms64m -Xmx1024m \
    -Dargouml.headless=true \
    -Dlog4j.configuration=/dev/null \
    -Djava.util.logging.config.file=/dev/null \
    -cp "$CP" \
    org.argouml.application.Main 2>&1
