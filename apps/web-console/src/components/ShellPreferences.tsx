import LocaleSwitcher from "./LocaleSwitcher";
import ThemeSwitcher from "./ThemeSwitcher";
import TimezoneSwitcher from "./TimezoneSwitcher";

/** Language + timezone + color theme controls for shell topbars and login. */
export default function ShellPreferences() {
  return (
    <>
      <ThemeSwitcher />
      <TimezoneSwitcher />
      <LocaleSwitcher />
    </>
  );
}
