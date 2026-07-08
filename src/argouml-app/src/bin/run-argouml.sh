#!/bin/sh
# Build a launcher for ArgoUML using full classpath from local Maven cache.
# This bypasses the broken `jar-with-dependencies` assembly in argouml-build.
#
# IMPORTANT: The "-Dargouml.modules=" list below loads the runtime extension
# modules (Sequence/Activity/State/Deployment diagram factories, new Notation
# framework, XML property panels, Transformers, Developer inspector).
# Without this list, Sequence Diagram creation silently fails — the
# createCollaboration() call adds a "unattachedCollaboration" element but
# no real ArgoDiagram is registered because DiagramFactory has no entry for
# DiagramType.Sequence. See AGENTS.md §"things that will trip you up #4" and
# `argouml-app/tools/eclipse/ArgoUML (live GEF).launch` for the source list.
export JAVA_HOME=${JAVA_HOME:-/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home}
export PATH=$JAVA_HOME/bin:$PATH

ARGO_HOME=/Users/lxd/Projects/ai/uml-project/argouml
CP="$ARGO_HOME/src/argouml-build/target/argouml-jar-with-dependencies.jar"

# Ensure transitive deps (incl. argouml-ai.jar) are on the classpath.
# The argouml-jar-with-dependencies.jar above does NOT bundle
# argouml-ai classes; without this, AI features fail with
# NoClassDefFoundError at first reference to org.argouml.ai.*.
if [ ! -d /tmp/argouml-deps ] || [ -z "$(ls /tmp/argouml-deps/*.jar 2>/dev/null)" ]; then
    echo "[run-argouml] /tmp/argouml-deps empty; populating from Maven cache..."
    if [ -z "$JAVA_HOME" ]; then
        export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
    fi
    if [ -d "$ARGO_HOME/src/argouml-app" ]; then
        mvn -f "$ARGO_HOME/src/argouml-app/pom.xml" \
            dependency:copy-dependencies \
            -DskipTests -o \
            -DoutputDirectory=/tmp/argouml-deps >/dev/null 2>&1
        if [ ! -d /tmp/argouml-deps ] || [ -z "$(ls /tmp/argouml-deps/*.jar 2>/dev/null)" ]; then
            echo "[run-argouml] WARNING: dependency copy failed;"
            echo "             argouml-ai.jar may not be on the classpath."
            echo "             Run 'mvn -pl src/argouml-app dependency:copy-dependencies -DoutputDirectory=/tmp/argouml-deps' manually."
        fi
    else
        echo "[run-argouml] WARNING: cannot find $ARGO_HOME/src/argouml-app"
    fi
fi

# All argouml transitive deps (GEF, batik, MDR, etc.)
for jar in /tmp/argouml-deps/*.jar; do
  CP="$CP:$jar"
done

# Optional: append an i18n bundle JAR to the classpath. Built from
# https://github.com/argouml-tigris-org/argouml-i18n-<lang> (see readme.txt).
# Example: ARGO_I18N_JAR=/tmp/argouml-i18n-zh.jar
# Layout expected inside the jar: org/argouml/i18n/*_<lang>_<COUNTRY>.properties
# Translator (org.argouml.i18n.Translator) picks them up via the system
# classloader once Locale matches; missing keys fall back to English.
if [ -n "$ARGO_I18N_JAR" ] && [ -f "$ARGO_I18N_JAR" ]; then
  CP="$CP:$ARGO_I18N_JAR"
fi

ARGO_MODULES="\
org.argouml.notation2.NotationModule;\
org.argouml.activity2.ActivityDiagramModule;\
org.argouml.state2.StateDiagramModule;\
org.argouml.deployment2.DeploymentDiagramModule;\
org.argouml.sequence2.SequenceDiagramModule;\
org.argouml.core.propertypanels.module.XmlPropertyPanelsModule;\
org.argouml.transformer.TransformerModule;\
org.argouml.dev.DeveloperModule"

# Optional JVM locale flags, e.g. ARGO_LANG=zh ARGO_COUNTRY=CN.
# Only take effect when an ARGO_I18N_JAR that supplies matching *_xx.properties
# is on the classpath.
LANG_ARGS=""
if [ -n "$ARGO_LANG" ]; then
  LANG_ARGS="-Duser.language=$ARGO_LANG"
  [ -n "$ARGO_COUNTRY" ] && LANG_ARGS="$LANG_ARGS -Duser.country=$ARGO_COUNTRY"
fi

exec java -Xms64m -Xmx1024m \
  --add-opens=java.desktop/com.apple.eawt=ALL-UNNAMED \
  -ea \
  "-Dargouml.modules=$ARGO_MODULES" \
  $LANG_ARGS \
  -cp "$CP" \
  org.argouml.application.Main "$@"