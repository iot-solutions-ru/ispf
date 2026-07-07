# Custom driver template

Copy this directory to `packages/ispf-driver-<your-id>/` and replace:

| Placeholder | Replace with |
| ----------- | ------------ |
| `acme-widget` | your `driverId` |
| `com.ispf.driver.template` | your Java package |
| `ispf-driver-acme-widget` | your Gradle module / pack id |
| `TemplateDeviceDriver` | your driver class name |

Then register the module in root `settings.gradle.kts` and add a row to `DriverProductionMatrix` when ready for promotion.
