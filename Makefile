# ChopCut — atalhos de build. Requer o JDK 17 do projeto em ./jdk17.
# Flags de performance (parallel, caching, daemon) vivem em gradle.properties — não aqui.
# Toggles pontuais de debug você passa na mão, ex.: make build GRADLE_ARGS="-i --rerun-tasks".

export JAVA_HOME := $(CURDIR)/jdk17
GRADLE        := ./gradlew
GRADLE_ARGS   ?=
ADB           := $(HOME)/Android/Sdk/platform-tools/adb
APP_ID        := com.chopcut
MAIN_ACTIVITY := $(APP_ID)/$(APP_ID).MainActivity

.PHONY: help compile build install run clean lint test

help: ## Lista os alvos disponíveis
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
		awk 'BEGIN{FS=":.*?## "}{printf "  \033[36m%-9s\033[0m %s\n", $$1, $$2}'

compile: ## Compila o Kotlin (checagem rápida)
	$(GRADLE) compileDebugKotlin $(GRADLE_ARGS)

build: ## Gera o APK de debug
	$(GRADLE) assembleDebug $(GRADLE_ARGS)

install: ## Instala no device/emulador conectado
	$(GRADLE) installDebug $(GRADLE_ARGS)

run: install ## Instala e abre o app no device
	$(ADB) shell am start -n $(MAIN_ACTIVITY)

clean: ## Limpa os outputs de build
	$(GRADLE) clean $(GRADLE_ARGS)

lint: ## Roda o lintDebug
	$(GRADLE) lintDebug $(GRADLE_ARGS)

test: ## Roda os testes instrumentados (requer device)
	$(GRADLE) connectedAndroidTest $(GRADLE_ARGS)
