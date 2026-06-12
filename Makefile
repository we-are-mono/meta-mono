PORT ?= 8000
DIST ?= dist

.PHONY: help build serve

help:
	@echo "Targets:"
	@echo "  make build   Build firmware via kas"
	@echo "  make serve   Serve $(DIST)/ over HTTP on port $(PORT)"
	@echo "               (use with: firmware update --url http://<host>:$(PORT))"

build:
	kas build

serve:
	@echo "Serving $(DIST)/ on port $(PORT) — Ctrl-C to stop"
	@cd $(DIST) && python3 -m http.server $(PORT)
