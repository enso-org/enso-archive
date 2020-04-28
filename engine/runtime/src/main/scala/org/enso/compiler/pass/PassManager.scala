package org.enso.compiler.pass

// TODO [AA] Passes should have a configuration
// TODO [AA] Needs to set the 'writeToContext' flag in pass configuration.
// TODO [AA] Create a `PassConfiguration` which is passed to the various passes.
//  Should be keyed on the pass name
// TODO [AA] Pass configuration

/** The pass manager is responsible for executing the provided passes in order.
 *
 * @param passOrdering the specification of the ordering for the passes
 */
class PassManager(passOrdering: List[IRPass]) {}
