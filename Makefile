PORT ?= 8000
DIST ?= dist

.PHONY: help build serve announce

help:
	@echo "Targets:"
	@echo "  make build     Build firmware via kas"
	@echo "  make serve     Serve $(DIST)/ over HTTP on port $(PORT)"
	@echo "                 (use with: firmware update --url http://<host>:$(PORT))"
	@echo "  make announce  Post the release notes to Discord (asks first;"
	@echo "                 YES=1 to skip the prompt)"

build:
	kas build

serve:
	@echo "Serving $(DIST)/ on port $(PORT) — Ctrl-C to stop"
	@cd $(DIST) && python3 -m http.server $(PORT)

announce:
	@python3 tools/announce-release.py $(if $(YES),--yes,)
