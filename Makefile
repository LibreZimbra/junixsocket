
all:
	mvn package -Dmaven.javadoc.skip=true -DskipTests
	cd junixsocket-native && mvn package -Dmaven.javadoc.skip=true -DskipTests

install:
	$(call mk_install_dir, lib)
	cp junixsocket-native/target/nar/junixsocket-native-*/lib/*/jni/libjunixsocket-native-*.so $(INSTALL_DIR)/lib
	cp junixsocket-native/target/junixsocket-native-*.nar                                      $(INSTALL_DIR)/lib

clean:
	rm -Rf junixsocket-common/target \
               junixsocket-demo/target \
               junixsocket-mysql/target \
               junixsocket-native/target \
               junixsocket-rmi/target

include build.mk
