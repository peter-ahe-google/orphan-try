// Copyright (c) 2012, the Dart project authors.  Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

#include "platform/assert.h"
#include "vm/bootstrap_natives.h"
#include "vm/class_finalizer.h"
#include "vm/dart.h"
#include "vm/dart_api_impl.h"
#include "vm/dart_entry.h"
#include "vm/exceptions.h"
#include "vm/lockers.h"
#include "vm/longjump.h"
#include "vm/message_handler.h"
#include "vm/object.h"
#include "vm/object_store.h"
#include "vm/port.h"
#include "vm/resolver.h"
#include "vm/snapshot.h"
#include "vm/symbols.h"
#include "vm/unicode.h"

namespace dart {

static uint8_t* allocator(uint8_t* ptr, intptr_t old_size, intptr_t new_size) {
  void* new_ptr = realloc(reinterpret_cast<void*>(ptr), new_size);
  return reinterpret_cast<uint8_t*>(new_ptr);
}


DEFINE_NATIVE_ENTRY(CapabilityImpl_factory, 1) {
  ASSERT(TypeArguments::CheckedHandle(arguments->NativeArgAt(0)).IsNull());
  uint64_t id = isolate->random()->NextUInt64();
  return Capability::New(id);
}


DEFINE_NATIVE_ENTRY(RawReceivePortImpl_factory, 1) {
  ASSERT(TypeArguments::CheckedHandle(arguments->NativeArgAt(0)).IsNull());
  Dart_Port port_id =
      PortMap::CreatePort(arguments->isolate()->message_handler());
  return ReceivePort::New(port_id, false /* not control port */);
}


DEFINE_NATIVE_ENTRY(RawReceivePortImpl_get_id, 1) {
  GET_NON_NULL_NATIVE_ARGUMENT(ReceivePort, port, arguments->NativeArgAt(0));
  return Integer::NewFromUint64(port.Id());
}


DEFINE_NATIVE_ENTRY(RawReceivePortImpl_get_sendport, 1) {
  GET_NON_NULL_NATIVE_ARGUMENT(ReceivePort, port, arguments->NativeArgAt(0));
  return port.send_port();
}


DEFINE_NATIVE_ENTRY(RawReceivePortImpl_closeInternal, 1) {
  GET_NON_NULL_NATIVE_ARGUMENT(ReceivePort, port, arguments->NativeArgAt(0));
  Dart_Port id = port.Id();
  PortMap::ClosePort(id);
  return Integer::NewFromUint64(id);
}


DEFINE_NATIVE_ENTRY(SendPortImpl_get_id, 1) {
  GET_NON_NULL_NATIVE_ARGUMENT(SendPort, port, arguments->NativeArgAt(0));
  return Integer::NewFromUint64(port.Id());
}


DEFINE_NATIVE_ENTRY(SendPortImpl_get_hashcode, 1) {
  GET_NON_NULL_NATIVE_ARGUMENT(SendPort, port, arguments->NativeArgAt(0));
  int64_t id = port.Id();
  int32_t hi = static_cast<int32_t>(id >> 32);
  int32_t lo = static_cast<int32_t>(id);
  int32_t hash = (hi ^ lo) & kSmiMax;
  return Smi::New(hash);
}


DEFINE_NATIVE_ENTRY(SendPortImpl_sendInternal_, 2) {
  GET_NON_NULL_NATIVE_ARGUMENT(SendPort, port, arguments->NativeArgAt(0));
  // TODO(iposva): Allow for arbitrary messages to be sent.
  GET_NON_NULL_NATIVE_ARGUMENT(Instance, obj, arguments->NativeArgAt(1));

  uint8_t* data = NULL;
  MessageWriter writer(&data, &allocator);
  writer.WriteMessage(obj);

  // TODO(turnidge): Throw an exception when the return value is false?
  PortMap::PostMessage(new Message(port.Id(),
                                   data, writer.BytesWritten(),
                                   Message::kNormalPriority));
  return Object::null();
}


static void ThrowIsolateSpawnException(const String& message) {
  const Array& args = Array::Handle(Array::New(1));
  args.SetAt(0, message);
  Exceptions::ThrowByType(Exceptions::kIsolateSpawn, args);
}


static bool CanonicalizeUri(Isolate* isolate,
                            const Library& library,
                            const String& uri,
                            char** canonical_uri,
                            char** error) {
  Zone* zone = isolate->current_zone();
  bool retval = false;
  Dart_LibraryTagHandler handler = isolate->library_tag_handler();
  if (handler != NULL) {
    Dart_EnterScope();
    Dart_Handle result = handler(Dart_kCanonicalizeUrl,
                                 Api::NewHandle(isolate, library.raw()),
                                 Api::NewHandle(isolate, uri.raw()));
    const Object& obj = Object::Handle(Api::UnwrapHandle(result));
    if (obj.IsString()) {
      *canonical_uri = zone->MakeCopyOfString(String::Cast(obj).ToCString());
      retval = true;
    } else if (obj.IsError()) {
      Error& error_obj = Error::Handle();
      error_obj ^= obj.raw();
      *error = zone->PrintToString("Unable to canonicalize uri '%s': %s",
                                   uri.ToCString(), error_obj.ToErrorCString());
    } else {
      *error = zone->PrintToString("Unable to canonicalize uri '%s': "
                                   "library tag handler returned wrong type",
                                   uri.ToCString());
    }
    Dart_ExitScope();
  } else {
    *error = zone->PrintToString(
        "Unable to canonicalize uri '%s': no library tag handler found.",
        uri.ToCString());
  }
  return retval;
}


static bool CreateIsolate(Isolate* parent_isolate,
                          IsolateSpawnState* state,
                          char** error) {
  Dart_IsolateCreateCallback callback = Isolate::CreateCallback();
  if (callback == NULL) {
    *error = strdup("Null callback specified for isolate creation\n");
    Isolate::SetCurrent(parent_isolate);
    return false;
  }

  void* init_data = parent_isolate->init_callback_data();
  Isolate* child_isolate = reinterpret_cast<Isolate*>(
      (callback)(state->script_url(),
                 state->function_name(),
                 state->package_root(),
                 init_data,
                 error));
  if (child_isolate == NULL) {
    Isolate::SetCurrent(parent_isolate);
    return false;
  }
  state->set_isolate(reinterpret_cast<Isolate*>(child_isolate));

  Isolate::SetCurrent(parent_isolate);
  return true;
}


static RawObject* Spawn(Isolate* parent_isolate,
                        IsolateSpawnState* state) {
  // Create a new isolate.
  char* error = NULL;
  if (!CreateIsolate(parent_isolate, state, &error)) {
    delete state;
    const String& msg = String::Handle(String::New(error));
    free(error);
    ThrowIsolateSpawnException(msg);
  }

  // Create a SendPort for the new isolate.
  Isolate* spawned_isolate = state->isolate();
  const SendPort& port = SendPort::Handle(
      SendPort::New(spawned_isolate->main_port()));

  // Start the new isolate if it is already marked as runnable.
  MutexLocker ml(spawned_isolate->mutex());
  spawned_isolate->set_spawn_state(state);
  if (spawned_isolate->is_runnable()) {
    spawned_isolate->Run();
  }

  return port.raw();
}


DEFINE_NATIVE_ENTRY(Isolate_spawnFunction, 3) {
  GET_NON_NULL_NATIVE_ARGUMENT(SendPort, port, arguments->NativeArgAt(0));
  GET_NON_NULL_NATIVE_ARGUMENT(Instance, closure, arguments->NativeArgAt(1));
  GET_NON_NULL_NATIVE_ARGUMENT(Instance, message, arguments->NativeArgAt(2));
  if (closure.IsClosure()) {
    Function& func = Function::Handle();
    func = Closure::function(closure);
    if (func.IsImplicitClosureFunction() && func.is_static()) {
#if defined(DEBUG)
      Context& ctx = Context::Handle();
      ctx = Closure::context(closure);
      ASSERT(ctx.num_variables() == 0);
#endif
      return Spawn(isolate, new IsolateSpawnState(port.Id(), func, message));
    }
  }
  const String& msg = String::Handle(String::New(
      "Isolate.spawn expects to be passed a static or top-level function"));
  Exceptions::ThrowArgumentError(msg);
  return Object::null();
}


DEFINE_NATIVE_ENTRY(Isolate_spawnUri, 5) {
  GET_NON_NULL_NATIVE_ARGUMENT(SendPort, port, arguments->NativeArgAt(0));
  GET_NON_NULL_NATIVE_ARGUMENT(String, uri, arguments->NativeArgAt(1));
  GET_NON_NULL_NATIVE_ARGUMENT(Instance, args, arguments->NativeArgAt(2));
  GET_NON_NULL_NATIVE_ARGUMENT(Instance, message, arguments->NativeArgAt(3));
  GET_NATIVE_ARGUMENT(String, package_root, arguments->NativeArgAt(4));

  // Canonicalize the uri with respect to the current isolate.
  char* error = NULL;
  char* canonical_uri = NULL;
  const Library& root_lib =
      Library::Handle(arguments->isolate()->object_store()->root_library());
  if (!CanonicalizeUri(arguments->isolate(), root_lib, uri,
                       &canonical_uri, &error)) {
    const String& msg = String::Handle(String::New(error));
    ThrowIsolateSpawnException(msg);
  }

  char* utf8_package_root = NULL;
  if (!package_root.IsNull()) {
    const intptr_t len = Utf8::Length(package_root);
    Zone* zone = isolate->current_zone();
    utf8_package_root = zone->Alloc<char>(len + 1);
    package_root.ToUTF8(reinterpret_cast<uint8_t*>(utf8_package_root), len);
    utf8_package_root[len] = '\0';
  }

  return Spawn(isolate, new IsolateSpawnState(
      port.Id(), canonical_uri, utf8_package_root, args, message));
}


DEFINE_NATIVE_ENTRY(Isolate_sendOOB, 2) {
  GET_NON_NULL_NATIVE_ARGUMENT(SendPort, port, arguments->NativeArgAt(0));
  GET_NON_NULL_NATIVE_ARGUMENT(Array, msg, arguments->NativeArgAt(1));

  // Make sure to route this request to the isolate library OOB mesage handler.
  msg.SetAt(0, Smi::Handle(Smi::New(Message::kIsolateLibOOBMsg)));

  uint8_t* data = NULL;
  MessageWriter writer(&data, &allocator);
  writer.WriteMessage(msg);

  PortMap::PostMessage(new Message(port.Id(),
                                   data, writer.BytesWritten(),
                                   Message::kOOBPriority));
  return Object::null();
}

}  // namespace dart
