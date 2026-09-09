package ca.on.oicr.gsi.shesmu.jira;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Check that a JIRA transitions response can be deserialised even when the server sends nulls for
 * the boolean flags on a field
 *
 * <p>JIRA answers {@code /issue/{id}/transitions?expand=transitions.fields} with {@code
 * "hasDefaultValue": null} for some fields. Jackson 3 turned on {@code
 * DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES} by default, so mapping those nulls into the
 * {@code boolean} components of {@link IssueField} throws and no issue can be transitioned at all.
 */
public class TransitionsResponseTest {

  /** A transitions response as JIRA sends it, with nulls for {@code hasDefaultValue} */
  private static final String TRANSITIONS_WITH_NULL_HAS_DEFAULT_VALUE =
      """
      {
        "expand": "transitions",
        "transitions": [
          {
            "id": "31",
            "name": "Close Issue",
            "to": {
              "description": "The issue is considered finished.",
              "id": "6",
              "name": "Closed"
            },
            "hasScreen": true,
            "isGlobal": true,
            "isInitial": false,
            "isAvailable": true,
            "isConditional": false,
            "fields": {
              "assignee": {
                "required": false,
                "name": "Assignee",
                "key": "assignee",
                "hasDefaultValue": null,
                "operations": ["set"]
              },
              "resolution": {
                "required": true,
                "name": "Resolution",
                "key": "resolution",
                "hasDefaultValue": null,
                "operations": ["set"]
              }
            }
          }
        ]
      }
      """;

  /** The same response with the flags present, which is the case that always worked */
  private static final String TRANSITIONS_WITH_FLAGS =
      """
      {
        "expand": "transitions",
        "transitions": [
          {
            "id": "31",
            "name": "Close Issue",
            "to": {
              "description": "The issue is considered finished.",
              "id": "6",
              "name": "Closed"
            },
            "fields": {
              "assignee": {
                "required": false,
                "name": "Assignee",
                "key": "assignee",
                "hasDefaultValue": true,
                "operations": ["set"]
              },
              "resolution": {
                "required": true,
                "name": "Resolution",
                "key": "resolution",
                "hasDefaultValue": false,
                "operations": ["set"]
              }
            }
          }
        ]
      }
      """;

  /** A response where the flags are missing rather than null */
  private static final String TRANSITIONS_WITHOUT_FLAGS =
      """
      {
        "transitions": [
          {
            "id": "31",
            "name": "Close Issue",
            "to": {
              "description": "The issue is considered finished.",
              "id": "6",
              "name": "Closed"
            },
            "fields": {
              "resolution": {
                "name": "Resolution",
                "key": "resolution",
                "operations": ["set"]
              }
            }
          }
        ]
      }
      """;

  public TransitionsResponseTest() {}

  private IssueField field(String json, String name) {
    final TransitionsResponse response =
        JiraConnection.MAPPER.readValue(json, TransitionsResponse.class);
    Assertions.assertEquals(1, response.transitions().size(), "Wrong number of transitions");
    final Transition transition = response.transitions().get(0);
    Assertions.assertEquals("Closed", transition.to().name(), "Wrong transition target");
    final IssueField field = transition.fields().get(name);
    Assertions.assertNotNull(field, "Missing the " + name + " field");
    return field;
  }

  /**
   * A null {@code hasDefaultValue} must deserialise instead of throwing, since JIRA sends nulls and
   * a failure here stops every transition
   */
  @Test
  public void testNullHasDefaultValueIsDeserialized() {
    Assertions.assertFalse(
        field(TRANSITIONS_WITH_NULL_HAS_DEFAULT_VALUE, "assignee").hasDefaultValue(),
        "A null hasDefaultValue must be treated as false");
    Assertions.assertFalse(
        field(TRANSITIONS_WITH_NULL_HAS_DEFAULT_VALUE, "resolution").hasDefaultValue(),
        "A null hasDefaultValue must be treated as false");
  }

  /** A null flag must not disturb the flags that were sent alongside it */
  @Test
  public void testRequiredSurvivesANullHasDefaultValue() {
    Assertions.assertFalse(
        field(TRANSITIONS_WITH_NULL_HAS_DEFAULT_VALUE, "assignee").required(),
        "Wrong required flag for assignee");
    Assertions.assertTrue(
        field(TRANSITIONS_WITH_NULL_HAS_DEFAULT_VALUE, "resolution").required(),
        "Wrong required flag for resolution");
  }

  /**
   * A field with a null {@code hasDefaultValue} must be one that {@link
   * JiraConnection#processTransition} fills in from the configured default values, which is only
   * true if the null becomes false
   */
  @Test
  public void testANullHasDefaultValueNeedsAValueSupplied() {
    final IssueField resolution = field(TRANSITIONS_WITH_NULL_HAS_DEFAULT_VALUE, "resolution");
    Assertions.assertTrue(
        resolution.required() && !resolution.hasDefaultValue(),
        "A required field with a null hasDefaultValue must be given a value in the transition"
            + " request");
  }

  /** Flags that are actually sent must still be read as sent */
  @Test
  public void testFlagsAreDeserialized() {
    Assertions.assertTrue(
        field(TRANSITIONS_WITH_FLAGS, "assignee").hasDefaultValue(),
        "Wrong hasDefaultValue for assignee");
    Assertions.assertFalse(
        field(TRANSITIONS_WITH_FLAGS, "resolution").hasDefaultValue(),
        "Wrong hasDefaultValue for resolution");
  }

  /** Absent flags are the same as null ones as far as the transition logic is concerned */
  @Test
  public void testAbsentFlagsAreDeserialized() {
    final IssueField resolution = field(TRANSITIONS_WITHOUT_FLAGS, "resolution");
    Assertions.assertFalse(
        resolution.hasDefaultValue(), "An absent hasDefaultValue must be treated as false");
    Assertions.assertFalse(resolution.required(), "An absent required must be treated as false");
  }
}
