package org.enso.languageserver.filemanager

import java.io.File
import java.time.OffsetDateTime

/**
  * A representation of the attributes of a file.
  *
  * @param creationTime creation time
  * @param lastAccessTime last access time
  * @param lastModifiedTime last modified time
  * @param kind type of [[FileSystemObject]], can be
  *   [[FileSystemObject.DirectoryTruncated]]
  * | [[FileSystemObject.FileEntry]]
  * | [[FileSystemObject.OtherEntry]]
  * @param byteSize size in bytes
  */
case class FileAttributes(
  creationTime: OffsetDateTime,
  lastAccessTime: OffsetDateTime,
  lastModifiedTime: OffsetDateTime,
  kind: FileSystemObject,
  byteSize: Long
)

object FileAttributes {

  def fromFileSystemAttributes(
    root: File,
    path: Path,
    attrs: FileSystemApi.Attributes
  ): FileAttributes =
    FileAttributes(
      creationTime     = attrs.creationTime,
      lastAccessTime   = attrs.lastAccessTime,
      lastModifiedTime = attrs.lastModifiedTime,
      kind             = FileSystemObject.fromEntry(root, path, attrs.kind),
      byteSize         = attrs.byteSize
    )

}
