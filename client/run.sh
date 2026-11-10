#!/usr/bin/env bash
set -euo pipefail

cd -- "$(dirname -- "${BASH_SOURCE[0]}")"

# A Snap-hosted IDE can export GTK paths pointing to incompatible native libraries.
# Remove those overrides for this process while preserving the desktop session.
exec env \
  -u LD_LIBRARY_PATH \
  -u LD_PRELOAD \
  -u GTK_PATH \
  -u GTK_MODULES \
  -u GTK_EXE_PREFIX \
  -u GTK_IM_MODULE_FILE \
  -u GIO_MODULE_DIR \
  -u GDK_PIXBUF_MODULE_FILE \
  -u GDK_PIXBUF_MODULEDIR \
  mvn compile exec:java "$@"
