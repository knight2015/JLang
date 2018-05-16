all: compiler runtime jdk

compiler:
	@echo "--- Building compiler ---"
	@ant -q

runtime: compiler
	@echo "--- Building runtime ---"
	@$(MAKE) -C runtime

jdk: compiler runtime
	@echo "--- Building JDK ---"
	@$(MAKE) -C jdk

clean:
	ant -q clean
	$(MAKE) -C runtime clean
	$(MAKE) -C jdk clean

.PHONY: compiler runtime jdk
