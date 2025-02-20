/*
 * junixsocket
 *
 * Copyright 2009-2021 Christian Kohlschütter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "config.h"
#include "reflection.h"

#include "exceptions.h"
#include "jniutil.h"

static jboolean doSetServerSocket = true;

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    initServerImpl
 * Signature: (Lcom/newsclub/net/unix/AFUNIXServerSocket;Lcom/newsclub/net/unix/AFUNIXSocketImpl;)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_initServerImpl(
                                                                                  JNIEnv * env, jclass clazz CK_UNUSED, jobject serverSocket, jobject impl)
{
    setObjectFieldValue(env, serverSocket, "impl", "Ljava/net/SocketImpl;",
                        impl);

    if (doSetServerSocket) {
        // no longer present in Java 16
        doSetServerSocket = setObjectFieldValueIfPossible(env, impl, "serverSocket", "Ljava/net/ServerSocket;",
                                      serverSocket);
    }
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    setPort
 * Signature: (Lorg/newsclub/net/unix/AFUNIXSocketAddress;I)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_setPort(
                                                                           JNIEnv * env, jclass clazz CK_UNUSED, jobject addr, jint port)
{
    jclass fileDescriptorClass = (*env)->GetObjectClass(env, addr);

    jobject fieldObject = addr;

    jfieldID portField;
    jfieldID holderField = (*env)->GetFieldID(env, fileDescriptorClass,
                                              "holder", "Ljava/net/InetSocketAddress$InetSocketAddressHolder;");
    if(holderField != NULL) {
        fieldObject = (*env)->GetObjectField(env, addr, holderField);
        jclass holderClass = (*env)->GetObjectClass(env, fieldObject);
        portField = (*env)->GetFieldID(env, holderClass, "port", "I");
    } else {
        portField = (*env)->GetFieldID(env, fileDescriptorClass, "port", "I");
    }
    if(portField == NULL) {
        _throwException(env, kExceptionSocketException,
                        "Cannot find field \"port\" in java.net.InetSocketAddress. Unsupported JVM?");
        return;
    }
    (*env)->SetIntField(env, fieldObject, portField, port);
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    attachCloseable
 * Signature: (Ljava/io/FileDescriptor;Ljava/io/Closeable;)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_attachCloseable(
                                                                                   JNIEnv * env, jclass clazz CK_UNUSED, jobject fdesc, jobject closeable)
{
    callObjectSetter(env, fdesc, "attach", "(Ljava/io/Closeable;)V", closeable);
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    currentRMISocket
 * Signature: ()Ljava/net/Socket;
 */
JNIEXPORT jobject JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_currentRMISocket
(JNIEnv *env, jclass clazz CK_UNUSED)
{
    jclass tcpTransport = (*env)->FindClass(env,
                                            "sun/rmi/transport/tcp/TCPTransport");
    if(tcpTransport == NULL) {
        return NULL;
    }
    jfieldID threadConnectionHandler = (*env)->GetStaticFieldID(env, tcpTransport,
                                                                "threadConnectionHandler", "Ljava/lang/ThreadLocal;");
    if(threadConnectionHandler == NULL) {
        return NULL;
    }
    jobject tl = (*env)->GetStaticObjectField(env, tcpTransport, threadConnectionHandler);
    if(tl == NULL) {
        return NULL;
    }
    jclass tlClass = (*env)->GetObjectClass(env, tl);
    if(tlClass == NULL) {
        return NULL;
    }
    jmethodID tlGet = (*env)->GetMethodID(env, tlClass,
                                          "get", "()Ljava/lang/Object;");
    if(tlGet == NULL) {
        return NULL;
    }
    jobject connHandler = (*env)->CallObjectMethod(env, tl,
                                                   tlGet);
    if(connHandler == NULL) {
        return NULL;
    }
    jclass connHandlerClass = (*env)->GetObjectClass(env, connHandler);
    if(connHandlerClass == NULL) {
        return NULL;
    }
    jfieldID socketField = (*env)->GetFieldID(env, connHandlerClass,
                                              "socket", "Ljava/net/Socket;");
    if(socketField == NULL) {
        return NULL;
    }
    jobject socket = (*env)->GetObjectField(env, connHandler, socketField);

    return socket;
}
