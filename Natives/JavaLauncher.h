#pragma once

#include <Foundation/Foundation.h>
#include "jni.h"

typedef jint JLI_Launch_func(int argc, const char ** argv, /* main argc, argc */
        int jargc, const char** jargv,          /* java args */
        int appclassc, const char** appclassv,  /* app classpath */
        const char* fullversion,                /* full version defined */
        const char* dotversion,                 /* dot version defined */
        const char* pname,                      /* program name */
        const char* lname,                      /* launcher name */
        jboolean javaargs,                      /* JAVA_ARGS */
        jboolean cpwildcard,                    /* classpath wildcard*/
        jboolean javaw,                         /* windows-only javaw */
        jint ergo                               /* ergonomics class policy */
);
JLI_Launch_func *pJLI_Launch;

int launchJVM(NSString *username, id launchTarget, int width, int height, int minVersion);

// The renderer this launch uses. "auto" resolves to one that meets the version's minimum OpenGL; returns nil (with
// *error set to a message for the player) when no bundled renderer can. Also sets RENDERER. Logs [RendererSelect].
NSString *resolveLaunchRenderer(NSDictionary *metadata, NSString **error);
