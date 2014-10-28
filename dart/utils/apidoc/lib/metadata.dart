library metadata;

import '../../../sdk/lib/_internal/compiler/implementation/mirrors/source_mirrors.dart';
import '../../../sdk/lib/_internal/compiler/implementation/mirrors/mirrors_util.dart';

/// Returns the metadata for the given string or null if not found.
InstanceMirror findMetadata(List<InstanceMirror> metadataList, String find) {
  return metadataList.firstWhere(
      (metadata) {
        if (metadata is TypeInstanceMirror) {
          return nameOf(metadata.representedType) == find;
        }
        return nameOf(metadata.type) == find;
      }, orElse: () => null);
}
