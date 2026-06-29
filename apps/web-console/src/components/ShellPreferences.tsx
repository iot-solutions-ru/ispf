import LocaleSwitcher from "./LocaleSwitcher";
import ThemeSwitcher from "./ThemeSwitcher";

/** Language + color theme controls for shell topbars and login. */
export default function ShellPreferences() {
  return (
    <>
      <ThemeSwitcher />
      <LocaleSwitcher />
    </>
  );
}
