#!/bin/sh
# Same as run-argouml.sh but with /tmp/argouml-deps/argouml-ai-*.jar
# prepended to classpath so the FRESH InitHttpServerSubsystem (47 routes,
# incl. 17 usecase) takes precedence over the stale copy baked into
# argouml-jar-with-dependencies.jar.
export JAVA_HOME=${JAVA_HOME:-/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home}
export PATH=$JAVA_HOME/bin:$PATH

ARGO_HOME=/Users/lxd/Projects/ai/uml-project/argouml

# Ensure /tmp/argouml-deps exists.
if [ ! -d /tmp/argouml-deps ] || [ -z "$(ls /tmp/argouml-deps/*.jar 2>/dev/null)" ]; then
    echo "[run-argouml-ai] /tmp/argouml-deps empty; populating from Maven cache..."
    if [ -d "$ARGO_HOME/src/argouml-app" ]; then
        mvn -f "$ARGO_HOME/src/argouml-app/pom.xml" \
            dependency:copy-dependencies \
            -DskipTests -o \
            -DoutputDirectory=/tmp/argouml-deps >/dev/null 2>&1
    fi
fi

# argouml-ai FIRST so the fresh InitHttpServerSubsystem (47 routes) wins
# over the stale copy in argouml-jar-with-dependencies.jar.
# Pick up every argouml-ai-*.jar (typically just one), dedup later.
argouml_ai_jars=$(ls /tmp/argouml-deps/argouml-ai-*.jar 2>/dev/null | tr '\n' ':' | sed 's/:$//')
CP="$argouml_ai_jars"
CP="$CP:$ARGO_HOME/src/argouml-build/target/argouml-jar-with-dependencies.jar"
for jar in /tmp/argouml-deps/*.jar; do
  case "$CP" in
    *"$jar"*) ;;
    *) CP="$CP:$jar" ;;
  esac
done

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

LANG_ARGS=""
if [ -n "$ARGO_LANG" ]; then
  LANG_ARGS="-Duser.language=$ARGO_LANG"
  [ -n "$ARGO_COUNTRY" ] && LANG_ARGS="$LANG_ARGS -Duser.country=$ARGO_COUNTRY"
fi

exec java -Xms64m -Xmx1024m \
  -ea \
  "-Dargouml.modules=$ARGO_MODULES" \
  $LANG_ARGS \
  -cp "$CP" \
  org.argouml.application.Main "$@"