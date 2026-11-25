#!/bin/sh
# ----------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#    https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
# ----------------------------------------------------------------------------

if [ -z "$MAVEN_SKIP_RC" ] ; then

  if [ -f /usr/local/etc/mavenrc ] ; then
    . /usr/local/etc/mavenrc
  fi

  if [ -f /etc/mavenrc ] ; then
    . /etc/mavenrc
  fi

  if [ -f "$HOME/.mavenrc" ] ; then
    . "$HOME/.mavenrc"
  fi

fi

# OS specific support.  $var _must_ be set to either true or false.
cygwin=false;
darwin=false;
mingw=false
case "$(uname)" in
  CYGWIN*) cygwin=true ;;
  MINGW*) mingw=true;;
  Darwin*) darwin=true
    # Use /usr/libexec/java_home if available, otherwise fall back
    # to JAVA_HOME
    if [ -z "$JAVA_HOME" ]; then
      if [ -x "/usr/libexec/java_home" ]; then
        JAVA_HOME="$(/usr/libexec/java_home)"; export JAVA_HOME
      fi
    fi
    ;;
esac

if [ -z "$JAVA_HOME" ] ; then
  JAVACMD="$(command -v java)"
else
  JAVACMD="$JAVA_HOME/bin/java"
fi

if [ ! -x "$JAVACMD" ] ; then
  echo "Error: JAVA_HOME is not defined correctly." >&2
  echo "  We cannot execute $JAVACMD" >&2
  exit 1
fi

find_maven_basedir() {
  if [ -z "$1" ]
  then
    echo "Path not specified to find_maven_basedir"
    return 1
  fi

  basedir="$1"
  wdir="$1"
  while [ "$wdir" != '/' ] ; do
    if [ -d "$wdir"/.mvn ] ; then
      basedir=$wdir
      break
    fi
    # workaround for JBEAP-8937 (on Solaris 10/Sparc)
    if [ -d "${wdir}" ]; then
      wdir=$(cd "$wdir/.." || exit 1; pwd)
    fi
    # end of workaround
  done
  printf '%s' "$(cd "$basedir" || exit 1; pwd)"
}

# concatenates all lines of a file
concat_lines() {
  if [ -f "$1" ]; then
    echo "$(tr -s '\n' ' ' < "$1")"
  fi
}

MAVEN_PROJECTBASEDIR="${MAVEN_BASEDIR:-$(find_maven_basedir "$(pwd)")}"
MAVEN_OPTS="$(concat_lines "$MAVEN_PROJECTBASEDIR/.mvn/jvm.config") $MAVEN_OPTS"

# For Cygwin, switch paths to Windows format before running java
if $cygwin ; then
  [ -n "$JAVA_HOME" ] &&
    JAVA_HOME=$(cygpath --path --windows "$JAVA_HOME")
  [ -n "$MAVEN_PROJECTBASEDIR" ] &&
    MAVEN_PROJECTBASEDIR=$(cygpath --path --windows "$MAVEN_PROJECTBASEDIR")
fi

WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain

WRAPPER_JAR="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.jar"
if [ -r "$WRAPPER_JAR" ]; then
    if [ "$MVNW_VERBOSE" = true ]; then
      echo "Found $WRAPPER_JAR"
    fi
else
    if [ "$MVNW_VERBOSE" = true ]; then
      echo "Couldn't find $WRAPPER_JAR, downloading it ..."
    fi

    if [ -n "$MVNW_REPOURL" ]; then
      wrapperUrl="$MVNW_REPOURL/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar"
    else
      wrapperUrl="https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar"
    fi
    while IFS="=" read -r key value; do
      case "$key" in (wrapperUrl) wrapperUrl="$value"; break ;;
      esac
    done < "$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.properties"
    if [ "$MVNW_VERBOSE" = true ]; then
      echo "Downloading from: $wrapperUrl"
    fi

    if command -v wget > /dev/null; then
        QUIET="--quiet"
        if [ "$MVNW_VERBOSE" = true ]; then
          QUIET=""
        fi
        if ! wget $QUIET -O "$WRAPPER_JAR" "$wrapperUrl" ; then
            echo "Error: Failed to download Maven wrapper from $wrapperUrl" >&2
            exit 1
        fi
    elif command -v curl > /dev/null; then
        QUIET="--silent"
        if [ "$MVNW_VERBOSE" = true ]; then
          QUIET=""
        fi
        if ! curl $QUIET -o "$WRAPPER_JAR" "$wrapperUrl" ; then
            echo "Error: Failed to download Maven wrapper from $wrapperUrl" >&2
            exit 1
        fi
    else
        echo "Error: Cannot find wget or curl to download Maven wrapper" >&2
        exit 1
    fi
fi

exec "$JAVACMD" \
  $JVM_CONFIG_MAVEN_PROPS \
  $MAVEN_OPTS \
  $MAVEN_DEBUG_OPTS \
  -classpath "$WRAPPER_JAR" \
  "-Dmaven.multiModuleProjectDirectory=${MAVEN_PROJECTBASEDIR}" \
  ${WRAPPER_LAUNCHER} ${MAVEN_CONFIG} "$@"
