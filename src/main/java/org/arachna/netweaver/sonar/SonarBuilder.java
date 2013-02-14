/**
 * 
 */
package org.arachna.netweaver.sonar;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.tasks.Maven;
import hudson.tasks.Maven.MavenInstallation;
import hudson.tools.ToolInstallation;

import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Logger;

import org.arachna.ant.AntHelper;
import org.arachna.netweaver.dc.types.DevelopmentComponent;
import org.arachna.netweaver.hudson.nwdi.DCWithJavaSourceAcceptingFilter;
import org.arachna.netweaver.hudson.nwdi.NWDIBuild;
import org.arachna.netweaver.hudson.nwdi.NWDIProject;
import org.arachna.netweaver.hudson.util.FilePathHelper;
import org.arachna.velocity.VelocityHelper;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Jenkins builder that executes the maven sonar plugin for NetWeaver
 * development components.
 * 
 * @author Dirk Weigenand
 */
public class SonarBuilder extends Builder {
    /**
     * Descriptor for {@link SonarBuilder}.
     */
    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    /**
     * Data bound constructor. Used for populating a {@link SonarBuilder}
     * instance from form fields in <code>config.jelly</code>.
     */
    @DataBoundConstructor
    public SonarBuilder() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean perform(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener)
        throws InterruptedException, IOException {
        boolean result = true;
        final NWDIBuild nwdiBuild = (NWDIBuild)build;
        final AntHelper antHelper =
            new AntHelper(FilePathHelper.makeAbsolute(build.getWorkspace()), nwdiBuild.getDevelopmentComponentFactory());
        final SonarPomGenerator pomGenerator =
            new SonarPomGenerator(antHelper, new VelocityHelper(listener.getLogger()).getVelocityEngine());
        String pomLocation = "";

        final MavenInstallation.DescriptorImpl descriptor =
            ToolInstallation.all().get(MavenInstallation.DescriptorImpl.class);
        final MavenInstallation[] installations = descriptor.getInstallations();

        if (installations != null && installations.length > 0) {
            final MavenInstallation maven = installations[0];
            final String jvmOptions = "";
            final String properties = "";

            for (final DevelopmentComponent component : nwdiBuild
                .getAffectedDevelopmentComponents(new DCWithJavaSourceAcceptingFilter())) {
                try {
                    pomLocation = String.format("%s/sonar-pom.xml", antHelper.getBaseLocation(component));
                    pomGenerator.execute(component, new FileWriter(pomLocation));

                    result |=
                        new Maven("sonar:sonar", maven.getName(), pomLocation, properties, jvmOptions).perform(
                            nwdiBuild, launcher, listener);
                }
                catch (final IOException ioe) {
                    Logger.getLogger("NWDI-Sonar-Plugin").warning(
                        String.format("Could not create %s:\n%s", pomLocation, ioe.getMessage()));
                }
            }
        }
        else {
            listener.getLogger().println("No maven installation found!");
        }

        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DescriptorImpl getDescriptor() {
        return DESCRIPTOR;
    }

    /**
     * Descriptor for {@link SonarBuilder}.
     */
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        /**
         * Create descriptor for NWDI-CheckStyle-Builder and load global
         * configuration data.
         */
        public DescriptorImpl() {
            load();
        }

        /**
         * 
         * {@inheritDoc}
         */
        @Override
        public boolean isApplicable(final Class<? extends AbstractProject> aClass) {
            return NWDIProject.class.equals(aClass);
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        @Override
        public String getDisplayName() {
            return "NWDI Sonar Builder";
        }
    }
}