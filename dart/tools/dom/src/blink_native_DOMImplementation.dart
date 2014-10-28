// Copyright (c) 2014, the Dart project authors.  Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

part of _blink;

// _Utils native entry points
class Blink_Utils {
  static window() native "Utils_window";

  static forwardingPrint(message) native "Utils_forwardingPrint";

  static spawnDomUri(uri) native "Utils_spawnDomUri";

  static register(document, tag, customType, extendsTagName) native "Utils_register";

  static createElement(document, tagName) native "Utils_createElement";

  static initializeCustomElement(element) native "Utils_initializeCustomElement";

  static changeElementWrapper(element, type) native "Utils_changeElementWrapper";
}

class Blink_DOMWindowCrossFrame {
  // FIXME: Return to using explicit cross frame entry points after roll to M35
  static get_history(_DOMWindowCrossFrame) native "Window_history_cross_frame_Getter";

  static get_location(_DOMWindowCrossFrame) native "Window_location_cross_frame_Getter";

  static get_closed(_DOMWindowCrossFrame) native "Window_closed_Getter";

  static get_opener(_DOMWindowCrossFrame) native "Window_opener_Getter";

  static get_parent(_DOMWindowCrossFrame) native "Window_parent_Getter";

  static get_top(_DOMWindowCrossFrame) native "Window_top_Getter";

  static close(_DOMWindowCrossFrame) native "Window_close_Callback";

  static postMessage(_DOMWindowCrossFrame, message, targetOrigin, [messagePorts]) native "Window_postMessage_Callback";
}

class Blink_HistoryCrossFrame {
  // _HistoryCrossFrame native entry points
  static back(_HistoryCrossFrame) native "History_back_Callback";

  static forward(_HistoryCrossFrame) native "History_forward_Callback";

  static go(_HistoryCrossFrame, distance) native "History_go_Callback";
}

class Blink_LocationCrossFrame {
  // _LocationCrossFrame native entry points
  static set_href(_LocationCrossFrame, h) native "Location_href_Setter";
}

class Blink_DOMStringMap {
  // _DOMStringMap native entry  points
  static containsKey(_DOMStringMap, key) native "DOMStringMap_containsKey_Callback";

  static item(_DOMStringMap, key) native "DOMStringMap_item_Callback";

  static setItem(_DOMStringMap, key, value) native "DOMStringMap_setItem_Callback";

  static remove(_DOMStringMap, key) native "DOMStringMap_remove_Callback";

  static get_keys(_DOMStringMap) native "DOMStringMap_getKeys_Callback";
}